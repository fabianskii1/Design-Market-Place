package com.lecture.course.controller;

import com.lecture.course.dto.AssetDto;
import com.lecture.course.dto.CourseDto;
import com.lecture.course.service.AssetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.net.URLEncoder;

/**
 * 디자인 자산 파이프라인 API.
 *
 * 경로를 /api/courses/** 아래에 둔 이유:
 * API Gateway의 라우팅 규칙이 이미 빌드된 이미지에 포함되어 있어
 * /api/assets/** 같은 새 프리픽스는 게이트웨이를 통과하지 못한다.
 * 게이트웨이를 재빌드하지 않고 동작시키기 위한 선택이다.
 */
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class AssetController {

    private final AssetService assetService;

    /**
     * POST /api/courses/{courseId}/asset
     * 원본 업로드 → 워터마크 삽입 → 저장 (판매자 본인만)
     */
    @PostMapping(value = "/{courseId}/asset", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CourseDto.ApiResponse<AssetDto.AssetResponse>> upload(
            @PathVariable Long courseId,
            @RequestParam("file") MultipartFile file,
            @RequestHeader("X-User-Id") Long userId) {

        AssetDto.AssetResponse response = assetService.upload(courseId, userId, file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CourseDto.ApiResponse.success(response));
    }

    /** GET /api/courses/{courseId}/asset - 자산 메타데이터 */
    @GetMapping("/{courseId}/asset")
    public ResponseEntity<CourseDto.ApiResponse<AssetDto.AssetResponse>> metadata(
            @PathVariable Long courseId) {
        return ResponseEntity.ok(CourseDto.ApiResponse.success(assetService.getMetadata(courseId)));
    }

    /** GET /api/courses/{courseId}/asset/preview - 가시적 워터마크 미리보기 (공개) */
    @GetMapping("/{courseId}/asset/preview")
    public ResponseEntity<byte[]> preview(@PathVariable Long courseId) {
        byte[] data = assetService.getPreview(courseId);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(data);
    }

    /** GET /api/courses/{courseId}/asset/download - 워터마크본 다운로드 (구매자·소유자) */
    @GetMapping("/{courseId}/asset/download")
    public ResponseEntity<byte[]> download(
            @PathVariable Long courseId,
            @RequestHeader("X-User-Id") Long userId) {

        AssetService.DownloadResult result = assetService.download(courseId, userId);
        String encoded = URLEncoder.encode(result.filename(), StandardCharsets.UTF_8).replace("+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encoded)
                .header("X-Content-Checksum", result.checksum())
                .cacheControl(CacheControl.noStore())
                .body(result.data());
    }

    /** DELETE /api/courses/{courseId}/asset (소유자만) */
    @DeleteMapping("/{courseId}/asset")
    public ResponseEntity<CourseDto.ApiResponse<Void>> delete(
            @PathVariable Long courseId,
            @RequestHeader("X-User-Id") Long userId) {
        assetService.delete(courseId, userId);
        return ResponseEntity.ok(CourseDto.ApiResponse.<Void>success(null));
    }

    /**
     * POST /api/courses/assets/verify
     * 임의의 이미지에서 워터마크를 추출해 출처를 확인한다.
     */
    @PostMapping(value = "/assets/verify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CourseDto.ApiResponse<AssetDto.VerifyResponse>> verify(
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(CourseDto.ApiResponse.success(assetService.verify(file)));
    }

    /** GET /api/courses/internal/{courseId}/asset/exists - 다른 서비스용 */
    @GetMapping("/internal/{courseId}/asset/exists")
    public ResponseEntity<Boolean> exists(@PathVariable Long courseId) {
        return ResponseEntity.ok(assetService.exists(courseId));
    }
}
