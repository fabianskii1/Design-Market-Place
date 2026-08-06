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
     * Gateway에서 전달한 X-User-Id 헤더로 디자이너 ID 추출
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
     * multipart/form-data, 필드명 file.
     * 응답으로 갱신된 CourseResponse 를 돌려주므로 프론트가 곧바로
     * originalUrl / thumbnailUrl 을 받아 화면에 반영할 수 있다.
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
     * GET /courses/{id}/asset/preview - 이미지 바이너리 응답
     *
     * img 태그의 src 로 직접 쓰이므로 인증 헤더 없이 접근 가능해야 한다.
     * 래퍼 없이 바이트를 그대로 내려준다.
     */
    @GetMapping("/{id}/asset/preview")
    public ResponseEntity<Resource> previewAsset(@PathVariable Long id) {
        Resource resource = courseService.loadAsset(id);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(courseService.assetContentType(id)))
                .header(HttpHeaders.CACHE_CONTROL, "max-age=3600")
                .body(resource);
    }

    /**
     * GET /courses/{id}/asset/download - 파일 다운로드
     */
    @GetMapping("/{id}/asset/download")
    public ResponseEntity<Resource> downloadAsset(@PathVariable Long id) {
        Resource resource = courseService.loadAsset(id);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    // ──────────────────────────────────────────────────────

    /**
     * GET /courses - 전체 디자인 목록
     */
    @GetMapping
    public ResponseEntity<CourseDto.ApiResponse<List<CourseDto.CourseResponse>>> getAllCourses() {
        return ResponseEntity.ok(
                CourseDto.ApiResponse.success(courseService.getAllCourses())
        );
    }

    /**
     * GET /courses/{id} - 디자인 상세
     */
    @GetMapping("/{id}")
    public ResponseEntity<CourseDto.ApiResponse<CourseDto.CourseResponse>> getCourse(
            @PathVariable Long id) {
        return ResponseEntity.ok(
                CourseDto.ApiResponse.success(courseService.getCourse(id))
        );
    }

    /**
     * GET /courses/category/{category} - 카테고리별 디자인
     */
    @GetMapping("/category/{category}")
    public ResponseEntity<CourseDto.ApiResponse<List<CourseDto.CourseResponse>>> getCoursesByCategory(
            @PathVariable Course.Category category) {
        return ResponseEntity.ok(
                CourseDto.ApiResponse.success(courseService.getCoursesByCategory(category))
        );
    }

    /**
     * GET /courses/internal/exists/{id} - 디자인 존재 여부 (Enrollment Service 호출)
     */
    @GetMapping("/internal/exists/{id}")
    public ResponseEntity<Boolean> existsCourse(@PathVariable Long id) {
        return ResponseEntity.ok(courseService.existsCourse(id));
    }

    /**
     * GET /courses/internal/{id} - 디자인 상세 조회 (Enrollment Service 내부 호출용)
     */
    @GetMapping("/internal/{id}")
    public ResponseEntity<CourseDto.CourseResponse> getCourseInternal(@PathVariable Long id) {
        return ResponseEntity.ok(courseService.getCourse(id));
    }

    /**
     * POST /courses/internal/{id}/enrollment-count - 구매자 수 증가 (Enrollment Service 호출)
     */
    @PostMapping("/internal/{id}/enrollment-count")
    public ResponseEntity<Void> increaseEnrollmentCount(@PathVariable Long id) {
        courseService.increaseEnrollmentCount(id);
        return ResponseEntity.ok().build();
    }

    /**
     * GET /courses/internal/recommend - 추천 서비스용 미구매 디자인 조회
     */
    @GetMapping("/internal/recommend")
    public ResponseEntity<List<CourseDto.CourseResponse>> getRecommendCourses(
            @RequestParam Course.Category category,
            @RequestParam(defaultValue = "") List<Long> excludeIds) {
        return ResponseEntity.ok(courseService.getRecommendCourses(category, excludeIds));
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
     * GET /courses/sales/me - 판매 대시보드 (강사 본인)
     * Gateway가 전달한 X-User-Id 헤더 사용
     */
    @GetMapping("/sales/me")
    public ResponseEntity<CourseDto.ApiResponse<CourseDto.SalesDashboardResponse>> getMySales(
            @RequestHeader("X-User-Id") Long instructorId) {
        return ResponseEntity.ok(
                CourseDto.ApiResponse.success(courseService.getMySales(instructorId))
        );
    }
}