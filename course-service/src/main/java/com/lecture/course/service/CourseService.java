package com.lecture.course.service;

import com.lecture.course.dto.CourseDto;
import com.lecture.course.entity.Course;
import com.lecture.course.exception.AssetAccessDeniedException;
import com.lecture.course.exception.AssetNotFoundException;
import com.lecture.course.client.WatermarkClient;
import com.lecture.course.repository.CourseRepository;
import com.lecture.course.repository.LicenseTierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseService {

    private final CourseRepository courseRepository;
    private final LicenseTierRepository licenseTierRepository;
    private final PaymentServiceClient paymentServiceClient;
    private final EnrollmentServiceClient enrollmentServiceClient;
    private final FileStorageService fileStorageService;
    private final WatermarkClient watermarkClient;

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

    // ── 디자인 자산 ────────────────────────────────────────

    /**
     * 디자인 자산(이미지) 업로드.
     *
     * 업로드된 한 장으로 두 벌을 만들어 저장한다.
     *  - 원본본: 비가시적 워터마크만. 구매자·소유자에게만 내려간다.
     *  - 미리보기본: 가시적 + 비가시적. 전체 공개된다.
     *
     * 두 벌을 분리하지 않으면 구매자도 워터마크가 박힌 이미지를 받게 된다.
     *
     * @param instructorId 요청자. 본인 디자인이 아니면 거부한다.
     */
    @Transactional
    public CourseDto.CourseResponse uploadAsset(Long courseId, MultipartFile file, Long instructorId) {
        Course course = findCourseById(courseId);

        if (!course.getInstructorId().equals(instructorId)) {
            throw new AssetAccessDeniedException("본인이 등록한 디자인만 파일을 올릴 수 있습니다.");
        }

        byte[] uploaded = fileStorageService.readUpload(file);

        // 가시적 워터마크에 찍을 이름. 지금은 디자인 제목을 쓴다.
        // user-service 연동이 붙으면 디자이너명으로 바꾸면 된다.
        String ownerLabel = course.getTitle();

        // 워터마크 가공은 watermark-service 가 담당한다. 이 서비스는 저장만 맡는다.
        byte[] originalBytes = watermarkClient.createOriginal(uploaded, courseId, instructorId);
        byte[] previewBytes = watermarkClient.createPreview(uploaded, courseId, instructorId, ownerLabel);

        // 교체 업로드면 이전 파일을 정리한다
        String previousOriginal = course.getOriginalUrl();
        String previousWatermark = course.getWatermarkUrl();

        String originalName = fileStorageService.storePng(courseId, originalBytes, "original");
        String previewName = fileStorageService.storePng(courseId, previewBytes, "preview");

        course.updateAssets(originalName, previewName, sha256(originalBytes));

        fileStorageService.deleteQuietly(previousOriginal);
        fileStorageService.deleteQuietly(previousWatermark);

        log.info("[Course] 자산 등록 완료 courseId={} original={} preview={}",
                courseId, originalName, previewName);

        return CourseDto.CourseResponse.from(course);
    }

    /**
     * 미리보기용 파일 로드.
     * 공개 엔드포인트이므로 언제나 워터마크본을 준다.
     */
    public Resource loadPreview(Long courseId) {
        Course course = findCourseById(courseId);

        if (course.getWatermarkUrl() == null || course.getWatermarkUrl().isBlank()) {
            throw new AssetNotFoundException("등록된 이미지가 없습니다: " + courseId);
        }
        return fileStorageService.load(course.getWatermarkUrl());
    }

    public String previewContentType(Long courseId) {
        Course course = findCourseById(courseId);
        return fileStorageService.contentTypeOf(course.getWatermarkUrl());
    }

    /**
     * 원본 다운로드.
     *
     * 소유자이거나 구매자여야 한다. 그 외에는 403.
     * 여기서 막지 않으면 워터마크 없는 판매용 원본이 그대로 새어나간다.
     */
    @Transactional
    public Resource loadOriginalForDownload(Long courseId, Long userId) {
        Course course = findCourseById(courseId);

        if (course.getOriginalUrl() == null || course.getOriginalUrl().isBlank()) {
            throw new AssetNotFoundException("등록된 이미지가 없습니다: " + courseId);
        }

        boolean isOwner = course.getInstructorId().equals(userId);
        if (!isOwner && !enrollmentServiceClient.hasPurchased(userId, courseId)) {
            throw new AssetAccessDeniedException("구매한 사용자만 내려받을 수 있습니다.");
        }

        // 소유자 본인의 확인 다운로드는 집계에서 제외한다
        if (!isOwner) {
            course.increaseDownloadCount();
        }

        // 구매자에게는 본인 ID가 심긴 사본을 준다. 유출 시 경로를 특정하기 위함이다.
        // 이벤트 처리가 아직 안 끝났으면 원본으로 폴백해 다운로드 자체는 막지 않는다.
        if (!isOwner) {
            var buyerCopy = watermarkClient.findBuyerCopy(courseId, userId);
            if (buyerCopy.isPresent()) {
                return new ByteArrayResource(buyerCopy.get());
            }
            log.warn("[Course] 구매자 사본이 아직 없습니다. 원본으로 폴백 courseId={} buyerId={}",
                    courseId, userId);
        }

        return fileStorageService.load(course.getOriginalUrl());
    }

    /**
     * 서비스 간 호출용 원본 로드.
     *
     * 사용자 권한 검사를 하지 않는다. internal 경로로만 노출되며
     * 게이트웨이 라우팅에 포함되지 않아 외부에서는 접근할 수 없다.
     */
    public Resource loadOriginalInternal(Long courseId) {
        Course course = findCourseById(courseId);

        if (course.getOriginalUrl() == null || course.getOriginalUrl().isBlank()) {
            throw new AssetNotFoundException("등록된 이미지가 없습니다: " + courseId);
        }
        return fileStorageService.load(course.getOriginalUrl());
    }

    public String downloadFileName(Long courseId) {
        Course course = findCourseById(courseId);
        return "design-" + courseId + ".png";
    }

    /**
     * 유출 사본 검증.
     *
     * 비가시적 워터마크를 추출해 어느 디자인·누구 소유였는지 확인한다.
     * 체크섬이 어긋나면 워터마크는 살아있되 파일이 재가공된 경우다.
     */
    public CourseDto.WatermarkVerifyResponse verifyAsset(MultipartFile file) {
        byte[] bytes = fileStorageService.readUpload(file);

        // 추출은 watermark-service 가 수행한다. 이 서비스는 결과를 디자인 정보와 대조만 한다.
        Map<String, Object> trace = watermarkClient.trace(bytes);

        boolean found = Boolean.TRUE.equals(trace.get("watermarkFound"));
        if (!found) {
            return CourseDto.WatermarkVerifyResponse.builder()
                    .watermarkFound(false)
                    .registered(false)
                    .message("워터마크를 찾을 수 없습니다. 이 플랫폼에서 배포된 파일이 아니거나 재인코딩으로 손실되었습니다.")
                    .build();
        }

        Long designId = asLong(trace.get("courseId"));
        Course course = designId == null ? null : courseRepository.findById(designId).orElse(null);

        return CourseDto.WatermarkVerifyResponse.builder()
                .watermarkFound(true)
                .registered(course != null)
                .designId(designId)
                .ownerId(asLong(trace.get("ownerId")))
                .checksumMatched(Boolean.TRUE.equals(trace.get("checksumMatched")))
                .message(course == null
                        ? "워터마크는 발견됐으나 해당 디자인이 더 이상 존재하지 않습니다."
                        : String.valueOf(trace.getOrDefault("message", "")))
                .build();
    }

    private Long asLong(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }

    private String sha256(byte[] data) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(data));
        } catch (Exception e) {
            throw new IllegalStateException("체크섬 계산에 실패했습니다.", e);
        }
    }


    // ── 조회 ──────────────────────────────────────────────

    public CourseDto.CourseResponse getCourse(Long id) {
        return CourseDto.CourseResponse.from(findCourseById(id));
    }

    public List<CourseDto.CourseResponse> getAllCourses() {
        return courseRepository.findByStatus(Course.Status.ACTIVE).stream()
                .map(CourseDto.CourseResponse::from)
                .collect(Collectors.toList());
    }

    public List<CourseDto.CourseResponse> getCoursesByCategory(Course.Category category) {
        return courseRepository.findByCategoryAndStatus(category, Course.Status.ACTIVE).stream()
                .map(CourseDto.CourseResponse::from)
                .collect(Collectors.toList());
    }

    public boolean existsCourse(Long id) {
        return courseRepository.existsById(id);
    }

    @Transactional
    public void increaseEnrollmentCount(Long courseId) {
        findCourseById(courseId).increaseEnrollmentCount();
    }

    public List<CourseDto.CourseResponse> getRecommendCourses(
            Course.Category category, List<Long> excludeCourseIds) {

        List<Course> courses = excludeCourseIds.isEmpty()
                ? courseRepository.findByCategoryAndStatus(category, Course.Status.ACTIVE)
                : courseRepository.findByCategoryAndStatusAndIdNotIn(
                        category, Course.Status.ACTIVE, excludeCourseIds);

        return courses.stream()
                .sorted((a, b) -> b.getEnrollmentCount() - a.getEnrollmentCount())
                .map(CourseDto.CourseResponse::from)
                .collect(Collectors.toList());
    }

    private Course findCourseById(Long id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new AssetNotFoundException("디자인을 찾을 수 없습니다: " + id));
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
