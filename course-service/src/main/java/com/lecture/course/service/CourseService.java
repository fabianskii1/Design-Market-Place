package com.lecture.course.service;

import com.lecture.course.dto.CourseDto;
import com.lecture.course.entity.Course;
import com.lecture.course.repository.CourseRepository;
import com.lecture.course.repository.LicenseTierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.math.BigDecimal;


@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseService {

    private final CourseRepository courseRepository;
    private final LicenseTierRepository licenseTierRepository;
    private final PaymentServiceClient paymentServiceClient;
    private final FileStorageService fileStorageService;

    /**
     * 디자인 등록 (디자이너만 가능 - SecurityConfig에서 role 검증)
     */
    @Transactional
    public CourseDto.CourseResponse createCourse(CourseDto.CreateRequest request, Long instructorId) {
        Course course = Course.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .category(request.getCategory())
                .price(request.getPrice())
                .instructorId(instructorId)
                .build();

        return CourseDto.CourseResponse.from(courseRepository.save(course));
    }

    /**
     * 디자인 자산(이미지) 업로드.
     *
     * 등록과 분리한 이유: multipart 와 JSON 을 한 요청에 섞으면
     * 프론트·게이트웨이 양쪽에서 다루기가 까다로워진다.
     * 등록이 성공한 뒤 파일만 따로 올리고, 실패하면 상세 화면에서 재시도한다.
     *
     * @param instructorId 요청자. 본인 디자인이 아니면 거부한다.
     */
    @Transactional
    public CourseDto.CourseResponse uploadAsset(Long courseId, MultipartFile file, Long instructorId) {
        Course course = findCourseById(courseId);

        if (!course.getInstructorId().equals(instructorId)) {
            throw new IllegalArgumentException("본인이 등록한 디자인만 파일을 올릴 수 있습니다.");
        }

        // 교체 업로드면 이전 파일을 정리한다
        String previous = course.getOriginalUrl();

        String storedName = fileStorageService.store(courseId, file);

        // 워터마크 처리는 아직 없으므로 원본을 썸네일로도 사용한다.
        // watermark-service 가 붙으면 thumbnailUrl 을 워터마크본으로 교체한다.
        course.updateAssets(storedName, storedName);

        if (previous != null && !previous.equals(storedName)) {
            fileStorageService.deleteQuietly(previous);
        }

        return CourseDto.CourseResponse.from(course);
    }

    /**
     * 미리보기용 파일 로드.
     * 저장된 파일명은 DB에만 있으므로 courseId 로 조회해서 꺼낸다.
     */
    public Resource loadAsset(Long courseId) {
        Course course = findCourseById(courseId);
        String storedName = course.getOriginalUrl();

        if (storedName == null || storedName.isBlank()) {
            throw new IllegalArgumentException("등록된 파일이 없습니다: " + courseId);
        }
        return fileStorageService.load(storedName);
    }

    /** 응답 헤더용 Content-Type */
    public String assetContentType(Long courseId) {
        Course course = findCourseById(courseId);
        return fileStorageService.contentTypeOf(course.getOriginalUrl());
    }

    /**
     * 디자인 단건 조회
     */
    public CourseDto.CourseResponse getCourse(Long id) {
        Course course = findCourseById(id);
        return CourseDto.CourseResponse.from(course);
    }

    /**
     * 전체 활성 디자인 목록 조회
     */
    public List<CourseDto.CourseResponse> getAllCourses() {
        return courseRepository.findByStatus(Course.Status.ACTIVE).stream()
                .map(CourseDto.CourseResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 카테고리별 디자인 조회
     */
    public List<CourseDto.CourseResponse> getCoursesByCategory(Course.Category category) {
        return courseRepository.findByCategoryAndStatus(category, Course.Status.ACTIVE).stream()
                .map(CourseDto.CourseResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 디자인 존재 여부 확인 (Enrollment Service → Course Service REST 호출용)
     */
    public boolean existsCourse(Long id) {
        return courseRepository.existsById(id);
    }

    /**
     * 구매자 수 증가 (Enrollment Service 구매 활성화 시 호출)
     */
    @Transactional
    public void increaseEnrollmentCount(Long courseId) {
        Course course = findCourseById(courseId);
        course.increaseEnrollmentCount();
    }

    /**
     * 추천 서비스용: 카테고리별 미구매 디자인 조회
     * - excludeCourseIds: 이미 구매한 디자인 ID 목록
     */
    public List<CourseDto.CourseResponse> getRecommendCourses(
            Course.Category category, List<Long> excludeCourseIds) {

        List<Course> courses = excludeCourseIds.isEmpty()
                ? courseRepository.findByCategoryAndStatus(category, Course.Status.ACTIVE)
                : courseRepository.findByCategoryAndStatusAndIdNotIn(
                        category, Course.Status.ACTIVE, excludeCourseIds);

        // 구매자 수 기준 내림차순 정렬
        return courses.stream()
                .sorted((a, b) -> b.getEnrollmentCount() - a.getEnrollmentCount())
                .map(CourseDto.CourseResponse::from)
                .collect(Collectors.toList());
    }

    private Course findCourseById(Long id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("디자인을 찾을 수 없습니다: " + id));
    }

    /**
     * 강의(디자인)별 라이선스 등급 목록 조회
     * - 등록 API는 Should 스프린트에서 추가 예정. 오늘은 조회만 가능(빈 목록 정상)
     */
    public List<CourseDto.LicenseTierResponse> getLicenseTiers(Long courseId) {
        return licenseTierRepository.findByCourseId(courseId).stream()
                .map(CourseDto.LicenseTierResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 판매 통계 조회 (마이페이지 판매 대시보드)
     * - X-User-Id(=instructorId) 기준으로 본인 강의만 집계
     */
    public CourseDto.SalesDashboardResponse getMySales(Long instructorId) {
        List<Course> myCourses = courseRepository.findByInstructorId(instructorId);
        List<Long> courseIds = myCourses.stream().map(Course::getId).collect(Collectors.toList());

        Map<Long, PaymentServiceClient.CourseSales> salesMap =
                paymentServiceClient.getSalesSummary(courseIds);

        // courseId -> 그 강의의 등급별 판매 목록 (salesByLicense 계산용)
        Map<Long, List<PaymentServiceClient.LicenseTierSales>> licenseSalesByCourse =
                paymentServiceClient.getLicenseTierSales(courseIds).stream()
                        .collect(Collectors.groupingBy(PaymentServiceClient.LicenseTierSales::courseId));

        List<CourseDto.SalesItem> items = myCourses.stream()
                .map(course -> {
                    PaymentServiceClient.CourseSales sales =
                            salesMap.getOrDefault(course.getId(),
                                    new PaymentServiceClient.CourseSales(0L, java.math.BigDecimal.ZERO));

                    List<CourseDto.LicenseSalesBreakdown> salesByLicense =
                            licenseSalesByCourse.getOrDefault(course.getId(), List.of()).stream()
                                    .map(lt -> {
                                        com.lecture.course.entity.LicenseTier.Tier tierEnum =
                                                lt.licenseTierId() == null ? null :
                                                        licenseTierRepository.findById(lt.licenseTierId())
                                                                .map(com.lecture.course.entity.LicenseTier::getTier)
                                                                .orElse(null);
                                        return CourseDto.LicenseSalesBreakdown.builder()
                                                .tier(tierEnum)
                                                .count(lt.salesCount())
                                                .revenue(lt.revenue())
                                                .build();
                                    })
                                    .filter(b -> b.getTier() != null)
                                    .collect(Collectors.toList());

                    return CourseDto.SalesItem.builder()
                            .courseId(course.getId())
                            .title(course.getTitle())
                            .salesCount(sales.salesCount())
                            .revenue(sales.totalRevenue())
                            .salesByLicense(salesByLicense)
                            .build();
                })
                .collect(Collectors.toList());

        long totalCount = items.stream().mapToLong(CourseDto.SalesItem::getSalesCount).sum();
        java.math.BigDecimal totalRevenue = items.stream()
                .map(CourseDto.SalesItem::getRevenue)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        // 월별 매출 합산: 여러 강의의 같은 달을 하나로 합친다.
        // yearMonth는 payment-service가 이미 "YYYY-MM"로 내려주므로 그대로 month에 쓴다.
        Map<String, CourseDto.MonthlyBreakdownItem> monthlyMap = new java.util.TreeMap<>();
        for (PaymentServiceClient.MonthlySales m : paymentServiceClient.getMonthlySales(courseIds)) {
            monthlyMap.merge(m.yearMonth(),
                    CourseDto.MonthlyBreakdownItem.builder()
                            .month(m.yearMonth())
                            .revenue(m.revenue())
                            .salesCount(m.salesCount())
                            .build(),
                    (a, b) -> CourseDto.MonthlyBreakdownItem.builder()
                            .month(a.getMonth())
                            .revenue(a.getRevenue().add(b.getRevenue()))
                            .salesCount(a.getSalesCount() + b.getSalesCount())
                            .build());
        }

        Long subscriberCount = paymentServiceClient.getSubscriberCount(instructorId);

        return CourseDto.SalesDashboardResponse.builder()
                .items(items)
                .totalSalesCount(totalCount)
                .totalRevenue(totalRevenue)
                .monthlyBreakdown(new java.util.ArrayList<>(monthlyMap.values()))
                .subscriberCount(subscriberCount)
                .build();
    }


    @Transactional
    public List<CourseDto.LicenseTierResponse> registerLicenseTiers(
            Long courseId, CourseDto.LicenseTierRegisterRequest request, Long instructorId) {

        Course course = findCourseById(courseId);
        if (!course.getInstructorId().equals(instructorId)) {
            throw new IllegalArgumentException("본인이 등록한 디자인만 라이선스 등급을 설정할 수 있습니다.");
        }

        List<com.lecture.course.entity.LicenseTier> saved = request.getTiers().stream()
                .map(tp -> licenseTierRepository.findByCourseIdAndTier(courseId, tp.getTier())
                        .map(existing -> com.lecture.course.entity.LicenseTier.builder()
                                .id(existing.getId())
                                .courseId(courseId)
                                .tier(tp.getTier())
                                .price(tp.getPrice())
                                .build())
                        .orElse(com.lecture.course.entity.LicenseTier.builder()
                                .courseId(courseId)
                                .tier(tp.getTier())
                                .price(tp.getPrice())
                                .build()))
                .map(licenseTierRepository::save)
                .collect(Collectors.toList());

        BigDecimal lowestPrice = saved.stream()
                .map(com.lecture.course.entity.LicenseTier::getPrice)
                .min(BigDecimal::compareTo)
                .orElse(course.getPrice());
        course.updateRepresentativePrice(lowestPrice);

        return saved.stream()
                .map(CourseDto.LicenseTierResponse::from)
                .collect(Collectors.toList());
    }

    public CourseDto.LicenseTierResponse getLicenseTier(Long courseId, Long tierId) {
        com.lecture.course.entity.LicenseTier tier = licenseTierRepository.findById(tierId)
                .filter(t -> t.getCourseId().equals(courseId))
                .orElseThrow(() -> new IllegalArgumentException("라이선스 등급을 찾을 수 없습니다: " + tierId));
        return CourseDto.LicenseTierResponse.from(tier);
    }
}
