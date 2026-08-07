package com.lecture.course.controller;

import com.lecture.course.dto.CourseDto;
import com.lecture.course.entity.Course;
import com.lecture.course.service.CourseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    /**
     * POST /courses - 디자인 등록 (디자이너만)
     */
    @PostMapping
    public ResponseEntity<CourseDto.ApiResponse<CourseDto.CourseResponse>> createCourse(
            @Valid @RequestBody CourseDto.CreateRequest request,
            @RequestHeader("X-User-Id") Long instructorId) {

        CourseDto.CourseResponse response = courseService.createCourse(request, instructorId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CourseDto.ApiResponse.success(response));
    }

    // ── 디자인 자산 (이미지) ────────────────────────────────

    /**
     * POST /courses/{id}/asset - 디자인 이미지 업로드
     *
     * 업로드된 한 장으로 원본본(비가시적)과 미리보기본(가시적+비가시적)을 함께 만든다.
     * 응답으로 갱신된 CourseResponse 를 돌려주므로 프론트가 곧바로
     * hasAsset / thumbnailUrl 을 받아 화면에 반영할 수 있다.
     */
    @PostMapping(value = "/{id}/asset", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CourseDto.ApiResponse<CourseDto.CourseResponse>> uploadAsset(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file,
            @RequestHeader("X-User-Id") Long instructorId) {

        CourseDto.CourseResponse response = courseService.uploadAsset(id, file, instructorId);
        return ResponseEntity.ok(CourseDto.ApiResponse.success(response));
    }

    /**
     * GET /courses/{id}/asset/preview - 워터마크본 바이너리 응답
     *
     * 공개 이미지이므로 언제나 워터마크가 박힌 쪽을 내려준다.
     * 자산이 없으면 404 (GlobalExceptionHandler 에서 매핑).
     */
    @GetMapping("/{id}/asset/preview")
    public ResponseEntity<Resource> previewAsset(@PathVariable Long id) {
        Resource resource = courseService.loadPreview(id);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(courseService.previewContentType(id)))
                .header(HttpHeaders.CACHE_CONTROL, "max-age=3600")
                .body(resource);
    }

    /**
     * GET /courses/{id}/asset/download - 원본 다운로드
     *
     * 소유자 또는 구매자만 받을 수 있다. 그 외에는 403.
     */
    @GetMapping("/{id}/asset/download")
    public ResponseEntity<Resource> downloadAsset(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {

        Resource resource = courseService.loadOriginalForDownload(id, userId);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + courseService.downloadFileName(id) + "\"")
                .body(resource);
    }

    /**
     * GET /courses/internal/{id}/asset/original - 판매용 원본 바이트
     *
     * watermark-service 가 구매자별 사본을 만들 때 원본을 가져가는 통로다.
     * 파일시스템을 공유하지 않고 HTTP 로 주고받기 위해 열어 둔다.
     * internal 경로라 게이트웨이를 통해서는 노출되지 않는다.
     */
    @GetMapping("/internal/{id}/asset/original")
    public ResponseEntity<Resource> internalOriginal(@PathVariable Long id) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(courseService.loadOriginalInternal(id));
    }

    /**
     * POST /courses/assets/verify - 유출 사본 검증
     *
     * 비가시적 워터마크를 추출해 어느 디자인·누구 소유였는지 확인한다.
     */
    @PostMapping(value = "/assets/verify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CourseDto.ApiResponse<CourseDto.WatermarkVerifyResponse>> verifyAsset(
            @RequestPart("file") MultipartFile file) {

        return ResponseEntity.ok(
                CourseDto.ApiResponse.success(courseService.verifyAsset(file))
        );
    }

    // ──────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<CourseDto.ApiResponse<List<CourseDto.CourseResponse>>> getAllCourses() {
        return ResponseEntity.ok(
                CourseDto.ApiResponse.success(courseService.getAllCourses())
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<CourseDto.ApiResponse<CourseDto.CourseResponse>> getCourse(
            @PathVariable Long id) {
        return ResponseEntity.ok(
                CourseDto.ApiResponse.success(courseService.getCourse(id))
        );
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<CourseDto.ApiResponse<List<CourseDto.CourseResponse>>> getCoursesByCategory(
            @PathVariable Course.Category category) {
        return ResponseEntity.ok(
                CourseDto.ApiResponse.success(courseService.getCoursesByCategory(category))
        );
    }

    @GetMapping("/internal/exists/{id}")
    public ResponseEntity<Boolean> existsCourse(@PathVariable Long id) {
        return ResponseEntity.ok(courseService.existsCourse(id));
    }

    @GetMapping("/internal/{id}")
    public ResponseEntity<CourseDto.CourseResponse> getCourseInternal(@PathVariable Long id) {
        return ResponseEntity.ok(courseService.getCourse(id));
    }

    @PostMapping("/internal/{id}/enrollment-count")
    public ResponseEntity<Void> increaseEnrollmentCount(@PathVariable Long id) {
        courseService.increaseEnrollmentCount(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/internal/recommend")
    public ResponseEntity<List<CourseDto.CourseResponse>> getRecommendCourses(
            @RequestParam Course.Category category,
            @RequestParam(defaultValue = "") List<Long> excludeIds) {
        return ResponseEntity.ok(courseService.getRecommendCourses(category, excludeIds));
    }

    /**
     * GET /courses/sales/me - 판매 대시보드 (디자이너 본인)
     */
    @GetMapping("/sales/me")
    public ResponseEntity<CourseDto.ApiResponse<CourseDto.SalesDashboardResponse>> getMySales(
            @RequestHeader("X-User-Id") Long instructorId) {
        return ResponseEntity.ok(
                CourseDto.ApiResponse.success(courseService.getMySales(instructorId))
        );
    }

    /**
     * GET /courses/{id}/license-tiers - 라이선스 등급 목록 조회
     */
    @GetMapping("/{id}/license-tiers")
    public ResponseEntity<CourseDto.ApiResponse<List<CourseDto.LicenseTierResponse>>> getLicenseTiers(
            @PathVariable Long id) {
        return ResponseEntity.ok(
                CourseDto.ApiResponse.success(courseService.getLicenseTiers(id))
        );
    }

    /**
     * POST /courses/{id}/license-tiers - 라이선스 등급별 가격 등록 (디자이너 본인만)
     */
    @PostMapping("/{id}/license-tiers")
    public ResponseEntity<CourseDto.ApiResponse<List<CourseDto.LicenseTierResponse>>> registerLicenseTiers(
            @PathVariable Long id,
            @Valid @RequestBody CourseDto.LicenseTierRegisterRequest request,
            @RequestHeader("X-User-Id") Long instructorId) {
        return ResponseEntity.ok(
                CourseDto.ApiResponse.success(
                        courseService.registerLicenseTiers(id, request, instructorId))
        );
    }

    /** 내부 호출: Enrollment Service가 구매 시점에 등급 가격 조회 */
    @GetMapping("/internal/{courseId}/license-tiers/{tierId}")
    public ResponseEntity<CourseDto.LicenseTierResponse> getLicenseTierInternal(
            @PathVariable Long courseId, @PathVariable Long tierId) {
        return ResponseEntity.ok(courseService.getLicenseTier(courseId, tierId));
    }
}