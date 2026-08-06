package com.lecture.course.service;

import com.lecture.course.client.EnrollmentClient;
import com.lecture.course.dto.AssetDto;
import com.lecture.course.entity.Course;
import com.lecture.course.entity.DesignAsset;
import com.lecture.course.repository.CourseRepository;
import com.lecture.course.repository.DesignAssetRepository;
import com.lecture.course.watermark.LsbWatermarker;
import com.lecture.course.watermark.VisibleWatermarker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssetService {

    private static final List<String> ALLOWED_TYPES = List.of("image/png", "image/jpeg", "image/webp");
    private static final long MAX_BYTES = 10L * 1024 * 1024;   // 10MB
    private static final int MAX_STORED_EDGE = 2400;            // 판매본 최대 변 길이
    private static final int MAX_PREVIEW_EDGE = 1000;           // 미리보기 최대 변 길이
    private static final String PAYLOAD_VERSION = "DMP1";

    private final DesignAssetRepository assetRepository;
    private final CourseRepository courseRepository;
    private final LsbWatermarker lsbWatermarker;
    private final VisibleWatermarker visibleWatermarker;
    private final EnrollmentClient enrollmentClient;

    // ────────────────────────────── 업로드 / 등록 ──────────────────────────────

    /**
     * 디자인 원본 업로드 → 워터마크 삽입 → 저장.
     *
     * 파이프라인:
     *   1. 검증 (형식·크기·소유권)
     *   2. 디코딩 및 리사이즈
     *   3. 비가시적 워터마크(LSB) 삽입 → 판매본 PNG
     *   4. 가시적 워터마크 합성 → 미리보기 PNG
     *   5. 두 결과와 메타데이터를 하나의 트랜잭션으로 저장
     */
    @Transactional
    public AssetDto.AssetResponse upload(Long courseId, Long userId, MultipartFile file) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("디자인을 찾을 수 없습니다: " + courseId));

        if (!course.getInstructorId().equals(userId)) {
            throw new AccessDeniedByOwnerException("본인이 등록한 디자인에만 파일을 업로드할 수 있습니다.");
        }
        validate(file);

        BufferedImage source = decode(file);
        BufferedImage stored = resize(source, MAX_STORED_EDGE);

        String assetToken = UUID.randomUUID().toString();
        String payload = buildPayload(assetToken, userId);

        BufferedImage watermarked = lsbWatermarker.embed(stored, payload);
        byte[] watermarkedBytes = encodePng(watermarked);

        BufferedImage previewBase = resize(stored, MAX_PREVIEW_EDGE);
        BufferedImage preview = visibleWatermarker.apply(previewBase, watermarkLabel(course));
        byte[] previewBytes = encodePng(preview);

        assetRepository.findByCourseId(courseId).ifPresent(existing -> {
            log.info("기존 자산을 교체합니다 - courseId: {}, assetId: {}", courseId, existing.getId());
            assetRepository.delete(existing);
            assetRepository.flush();
        });

        DesignAsset asset = DesignAsset.builder()
                .courseId(courseId)
                .ownerId(userId)
                .assetToken(assetToken)
                .originalFilename(safeFilename(file.getOriginalFilename()))
                .contentType("image/png")
                .width(watermarked.getWidth())
                .height(watermarked.getHeight())
                .fileSize((long) watermarkedBytes.length)
                .checksum(sha256(watermarkedBytes))
                .watermarkPayload(payload)
                .watermarkedData(watermarkedBytes)
                .previewData(previewBytes)
                .build();

        DesignAsset saved = assetRepository.save(asset);
        log.info("자산 업로드 완료 - courseId: {}, assetId: {}, token: {}, size: {}KB",
                courseId, saved.getId(), assetToken, watermarkedBytes.length / 1024);

        return toResponse(saved);
    }

    // ────────────────────────────── 조회 ──────────────────────────────

    public AssetDto.AssetResponse getMetadata(Long courseId) {
        return toResponse(findByCourseId(courseId));
    }

    public boolean exists(Long courseId) {
        return assetRepository.existsByCourseId(courseId);
    }

    /** 미리보기는 인증 없이 공개 */
    public byte[] getPreview(Long courseId) {
        return assetRepository.findPreviewData(courseId)
                .orElseThrow(() -> new IllegalArgumentException("등록된 디자인 파일이 없습니다: " + courseId));
    }

    /**
     * 워터마크본 다운로드.
     * 판매자 본인이거나 구매(수강 ACTIVE)한 사용자만 허용한다.
     */
    @Transactional
    public DownloadResult download(Long courseId, Long userId) {
        DesignAsset asset = findByCourseId(courseId);

        boolean isOwner = asset.getOwnerId().equals(userId);
        if (!isOwner && !enrollmentClient.hasPurchased(userId, courseId)) {
            throw new AccessDeniedByOwnerException("구매한 사용자만 원본을 내려받을 수 있습니다.");
        }

        byte[] data = assetRepository.findWatermarkedData(courseId)
                .orElseThrow(() -> new IllegalArgumentException("파일 데이터를 찾을 수 없습니다: " + courseId));

        asset.increaseDownloadCount();
        log.info("자산 다운로드 - courseId: {}, userId: {}, owner: {}, token: {}",
                courseId, userId, isOwner, asset.getAssetToken());

        return new DownloadResult(data, downloadFilename(asset), asset.getChecksum());
    }

    @Transactional
    public void delete(Long courseId, Long userId) {
        DesignAsset asset = findByCourseId(courseId);
        if (!asset.getOwnerId().equals(userId)) {
            throw new AccessDeniedByOwnerException("본인이 업로드한 파일만 삭제할 수 있습니다.");
        }
        assetRepository.delete(asset);
    }

    // ────────────────────────────── 워터마크 검증 ──────────────────────────────

    /**
     * 임의의 이미지에서 워터마크를 추출해 우리가 배포한 자산인지 대조한다.
     * 유출 사본의 출처(누구에게 배포된 파일인지)를 특정하는 데 사용한다.
     */
    public AssetDto.VerifyResponse verify(MultipartFile file) {
        validate(file);
        BufferedImage image = decode(file);

        Optional<String> extracted = lsbWatermarker.extract(image);
        if (extracted.isEmpty()) {
            return AssetDto.VerifyResponse.builder()
                    .watermarkFound(false)
                    .registered(false)
                    .message("워터마크를 찾을 수 없습니다. 워터마크가 없는 이미지이거나, "
                            + "손실 압축·리사이즈·재저장 과정에서 훼손되었을 수 있습니다.")
                    .build();
        }

        String payload = extracted.get();
        String[] parts = payload.split("\\|");
        if (parts.length != 4 || !PAYLOAD_VERSION.equals(parts[0])) {
            return AssetDto.VerifyResponse.builder()
                    .watermarkFound(true)
                    .payload(payload)
                    .registered(false)
                    .message("워터마크는 발견했으나 형식을 해석할 수 없습니다.")
                    .build();
        }

        String token = parts[1];
        Optional<DesignAsset> found = assetRepository.findByAssetToken(token);
        if (found.isEmpty()) {
            return AssetDto.VerifyResponse.builder()
                    .watermarkFound(true)
                    .payload(payload)
                    .assetToken(token)
                    .registered(false)
                    .message("워터마크는 유효하나 등록된 자산이 아닙니다. 삭제되었거나 다른 환경에서 발급된 파일입니다.")
                    .build();
        }

        DesignAsset asset = found.get();
        String uploadedChecksum = sha256(readAllBytes(file));
        boolean checksumMatched = uploadedChecksum.equals(asset.getChecksum());

        String title = courseRepository.findById(asset.getCourseId())
                .map(Course::getTitle)
                .orElse(null);

        return AssetDto.VerifyResponse.builder()
                .watermarkFound(true)
                .payload(payload)
                .registered(true)
                .assetToken(token)
                .courseId(asset.getCourseId())
                .courseTitle(title)
                .ownerId(asset.getOwnerId())
                .issuedAt(parseIssuedAt(parts[3]))
                .checksumMatched(checksumMatched)
                .message(checksumMatched
                        ? "배포한 원본과 바이트 단위로 일치합니다."
                        : "워터마크는 일치하나 파일이 재가공되었습니다(리사이즈·재저장 등).")
                .build();
    }

    // ────────────────────────────── 내부 유틸 ──────────────────────────────

    public record DownloadResult(byte[] data, String filename, String checksum) {}

    /** 소유권·권한 위반 (GlobalExceptionHandler에서 403으로 변환) */
    public static class AccessDeniedByOwnerException extends RuntimeException {
        public AccessDeniedByOwnerException(String message) { super(message); }
    }

    private DesignAsset findByCourseId(Long courseId) {
        return assetRepository.findByCourseId(courseId)
                .orElseThrow(() -> new IllegalArgumentException("등록된 디자인 파일이 없습니다: " + courseId));
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 파일이 비어 있습니다.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("파일 크기는 10MB를 넘을 수 없습니다. 현재: "
                    + (file.getSize() / 1024 / 1024) + "MB");
        }
        String type = file.getContentType();
        if (type == null || !ALLOWED_TYPES.contains(type.toLowerCase())) {
            throw new IllegalArgumentException("지원하지 않는 형식입니다: " + type
                    + " (허용: PNG, JPEG, WEBP)");
        }
    }

    private BufferedImage decode(MultipartFile file) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(file.getBytes())) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                throw new IllegalArgumentException("이미지를 읽을 수 없습니다. 파일이 손상되었을 수 있습니다.");
            }
            return image;
        } catch (IOException e) {
            throw new IllegalArgumentException("이미지 디코딩에 실패했습니다: " + e.getMessage(), e);
        }
    }

    /** 긴 변을 maxEdge 이하로 축소한다. 이미 작으면 그대로 반환 */
    private BufferedImage resize(BufferedImage source, int maxEdge) {
        int w = source.getWidth();
        int h = source.getHeight();
        int longEdge = Math.max(w, h);
        if (longEdge <= maxEdge && source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }
        double scale = longEdge <= maxEdge ? 1.0 : (double) maxEdge / longEdge;
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(h * scale));

        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, nw, nh);   // 투명 PNG를 RGB로 옮길 때 검게 변하는 것 방지
        g.drawImage(source, 0, 0, nw, nh, null);
        g.dispose();
        return out;
    }

    /** LSB는 무손실 포맷에서만 살아남는다 */
    private byte[] encodePng(BufferedImage image) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", out)) {
                throw new IllegalStateException("PNG 인코더를 찾을 수 없습니다.");
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("이미지 인코딩에 실패했습니다: " + e.getMessage(), e);
        }
    }

    private String buildPayload(String token, Long ownerId) {
        return String.join("|", PAYLOAD_VERSION, token,
                String.valueOf(ownerId), String.valueOf(Instant.now().getEpochSecond()));
    }

    private LocalDateTime parseIssuedAt(String epochSeconds) {
        try {
            return LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(Long.parseLong(epochSeconds)), ZoneId.systemDefault());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String watermarkLabel(Course course) {
        return "DesignMarket #" + course.getId();
    }

    private String downloadFilename(DesignAsset asset) {
        String base = asset.getOriginalFilename();
        int dot = base.lastIndexOf('.');
        if (dot > 0) base = base.substring(0, dot);
        return base + "_watermarked.png";
    }

    private String safeFilename(String name) {
        if (name == null || name.isBlank()) return "upload.png";
        String cleaned = name.replaceAll("[\\\\/]", "_");
        return cleaned.length() > 200 ? cleaned.substring(cleaned.length() - 200) : cleaned;
    }

    private byte[] readAllBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("파일을 읽을 수 없습니다: " + e.getMessage(), e);
        }
    }

    private String sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (Exception e) {
            throw new IllegalStateException("체크섬 계산에 실패했습니다.", e);
        }
    }

    private AssetDto.AssetResponse toResponse(DesignAsset asset) {
        return AssetDto.AssetResponse.builder()
                .assetId(asset.getId())
                .courseId(asset.getCourseId())
                .ownerId(asset.getOwnerId())
                .assetToken(asset.getAssetToken())
                .originalFilename(asset.getOriginalFilename())
                .contentType(asset.getContentType())
                .width(asset.getWidth())
                .height(asset.getHeight())
                .fileSize(asset.getFileSize())
                .checksum(asset.getChecksum())
                .downloadCount(asset.getDownloadCount())
                .createdAt(asset.getCreatedAt())
                .previewUrl("/api/courses/" + asset.getCourseId() + "/asset/preview")
                .downloadUrl("/api/courses/" + asset.getCourseId() + "/asset/download")
                .build();
    }
}
