package com.lecture.course.service;

import com.lecture.course.dto.CourseDto;
import com.lecture.course.entity.Course;
import com.lecture.course.exception.AssetAccessDeniedException;
import com.lecture.course.exception.AssetNotFoundException;
import com.lecture.course.repository.CourseRepository;
import com.lecture.course.repository.LicenseTierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final WatermarkService watermarkService;

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

        WatermarkService.WatermarkResult result =
                watermarkService.process(uploaded, courseId, instructorId, ownerLabel);

        // 교체 업로드면 이전 파일을 정리한다
        String previousOriginal = course.getOriginalUrl();
        String previousWatermark = course.getWatermarkUrl();

        String originalName = fileStorageService.storePng(courseId, result.originalBytes(), "original");
        String previewName = fileStorageService.storePng(courseId, result.previewBytes(), "preview");

        course.updateAssets(originalName, previewName, result.checksum());

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
        WatermarkService.ExtractResult extracted = watermarkService.extract(bytes);

        if (!extracted.found()) {
            return CourseDto.WatermarkVerifyResponse.builder()
                    .watermarkFound(false)
                    .registered(false)
                    .message("워터마크를 찾을 수 없습니다. 이 플랫폼에서 배포된 파일이 아니거나 재인코딩으로 손실되었습니다.")
                    .build();
        }

        Course course = courseRepository.findById(extracted.courseId()).orElse(null);

        if (course == null) {
            return CourseDto.WatermarkVerifyResponse.builder()
                    .watermarkFound(true)
                    .registered(false)
                    .designId(extracted.courseId())
                    .ownerId(extracted.ownerId())
                    .issuedAt(toLocalDateTime(extracted))
                    .message("워터마크는 발견됐으나 해당 디자인이 더 이상 존재하지 않습니다.")
                    .build();
        }

        String uploadedChecksum = watermarkService.sha256(bytes);
        boolean checksumMatched = uploadedChecksum.equals(course.getAssetChecksum());

        return CourseDto.WatermarkVerifyResponse.builder()
                .watermarkFound(true)
                .registered(true)
                .designId(course.getId())
                .ownerId(extracted.ownerId())
                .issuedAt(toLocalDateTime(extracted))
                .checksumMatched(checksumMatched)
                .message(checksumMatched
                        ? "원본과 일치하는 파일입니다."
                        : "워터마크는 일치하나 파일이 재가공되었습니다.")
                .build();
    }

    private LocalDateTime toLocalDateTime(WatermarkService.ExtractResult extracted) {
        return extracted.issuedAt() == null
                ? null
                : LocalDateTime.ofInstant(extracted.issuedAt(), ZoneId.systemDefault());
    }

    // ── 라이선스 등급 ──────────────────────────────────────

    /**
     * 디자인의 등급별 가격 목록 조회.
     * 등급이 아직 등록되지 않았으면 빈 목록을 돌려준다 (에러 아님).
     */
    public List<CourseDto.LicenseTierResponse> getLicenseTiers(Long courseId) {
        findCourseById(courseId);

        return licenseTierRepository.findByCourseId(courseId).stream()
                .map(CourseDto.LicenseTierResponse::from)
                .collect(Collectors.toList());
    }

    // ── 판매 대시보드 ──────────────────────────────────────

    /**
     * 디자이너 본인의 판매 실적.
     * 별도 집계 테이블 없이 실시간 쿼리로 처리한다.
     */
    public CourseDto.SalesDashboardResponse getMySales(Long instructorId) {
        List<Course> myCourses = courseRepository.findByInstructorId(instructorId);

        List<Long> courseIds = myCourses.stream().map(Course::getId).toList();
        Map<Long, PaymentServiceClient.CourseSales> sales =
                paymentServiceClient.getSalesSummary(courseIds);

        List<CourseDto.SalesItemResponse> items = myCourses.stream()
                .map(course -> {
                    PaymentServiceClient.CourseSales s = sales.get(course.getId());
                    return CourseDto.SalesItemResponse.from(
                            course,
                            s == null ? 0 : s.salesCount().intValue(),
                            s == null ? BigDecimal.ZERO : s.totalRevenue());
                })
                .sorted((a, b) -> b.getRevenue().compareTo(a.getRevenue()))
                .collect(Collectors.toList());

        int totalSalesCount = items.stream()
                .mapToInt(CourseDto.SalesItemResponse::getSalesCount)
                .sum();

        BigDecimal totalRevenue = items.stream()
                .map(CourseDto.SalesItemResponse::getRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return CourseDto.SalesDashboardResponse.builder()
                .instructorId(instructorId)
                .designCount(items.size())
                .totalSalesCount(totalSalesCount)
                .totalRevenue(totalRevenue)
                .items(items)
                .build();
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
}
