package com.lecture.course.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/**
 * 워터마크 처리.
 *
 * 두 종류를 다룬다.
 *  - 가시적: 미리보기 이미지 위에 반복 텍스트를 얹어 무단 사용을 억제한다.
 *  - 비가시적: 픽셀 파랑 채널 최하위 비트에 식별자를 심어 유출 추적에 쓴다.
 *
 * 비가시적 워터마크는 무손실 포맷에서만 살아남는다. JPEG로 재인코딩하면
 * 최하위 비트가 파괴되므로 결과물은 항상 PNG로 저장한다.
 */
@Slf4j
@Service
public class WatermarkService {

    /** 페이로드 포맷 버전. 나중에 형식이 바뀌어도 구버전을 식별할 수 있게 앞에 붙인다. */
    private static final String PAYLOAD_VERSION = "DMP1";
    private static final String PAYLOAD_DELIMITER = "|";

    /** LSB 헤더: 매직(32bit) + 페이로드 길이(32bit) */
    private static final int MAGIC = 0x444D5031; // "DMP1"
    private static final int HEADER_BITS = 64;

    /** 비가시 워터마크를 심으려면 최소한 이 정도 픽셀은 필요하다 */
    private static final int MIN_SHORT_EDGE = 400;

    // 가시적 워터마크 설정
    private static final float TILE_OPACITY = 0.30f;
    private static final double TILE_ANGLE_DEGREES = -30;
    private static final int TILE_GAP_X = 260;
    private static final int TILE_GAP_Y = 160;

    // ── 공개 API ──────────────────────────────────────────

    /** 처리 결과 한 벌 */
    public record WatermarkResult(
            byte[] originalBytes,   // 판매용: 비가시적만
            byte[] previewBytes,    // 공개용: 가시적 + 비가시적
            String payload,         // 삽입한 식별 문자열
            String checksum         // 원본 PNG의 SHA-256 (재가공 판별용)
    ) {}

    /**
     * 업로드된 이미지 한 장으로 원본본과 미리보기본을 만든다.
     *
     * @param ownerLabel 가시적 워터마크에 찍을 이름 (디자이너명 등)
     */
    public WatermarkResult process(byte[] uploadedBytes, Long courseId, Long ownerId, String ownerLabel) {
        BufferedImage source = readImage(uploadedBytes);
        validateSize(source);

        String payload = buildPayload(courseId, ownerId);

        // 1) 판매용 원본: 비가시적 워터마크만 삽입
        BufferedImage originalImage = embedInvisible(toRgb(source), payload);
        byte[] originalBytes = toPngBytes(originalImage);

        // 2) 공개 미리보기: 가시적 워터마크를 얹은 뒤 비가시적도 삽입
        //    순서가 중요하다. 가시적 처리가 픽셀을 바꾸므로 반드시 그 뒤에 LSB를 심어야 한다.
        BufferedImage visible = drawVisibleWatermark(toRgb(source), courseId, ownerLabel);
        BufferedImage previewImage = embedInvisible(visible, payload);
        byte[] previewBytes = toPngBytes(previewImage);

        log.info("[Watermark] 처리 완료 courseId={} payload={} originalSize={}B previewSize={}B",
                courseId, payload, originalBytes.length, previewBytes.length);

        return new WatermarkResult(originalBytes, previewBytes, payload, sha256(originalBytes));
    }

    /** 추출 결과 */
    public record ExtractResult(boolean found, String payload, Long courseId, Long ownerId, Instant issuedAt) {

        public static ExtractResult notFound() {
            return new ExtractResult(false, null, null, null, null);
        }
    }

    /** 유출 사본에서 비가시적 워터마크를 추출한다 */
    public ExtractResult extract(byte[] imageBytes) {
        try {
            BufferedImage image = toRgb(readImage(imageBytes));
            String payload = extractInvisible(image);

            if (payload == null || !payload.startsWith(PAYLOAD_VERSION + PAYLOAD_DELIMITER)) {
                return ExtractResult.notFound();
            }

            // DMP1|{courseId}|{ownerId}|{epochSeconds}
            String[] parts = payload.split("\\" + PAYLOAD_DELIMITER);
            if (parts.length < 4) {
                return ExtractResult.notFound();
            }

            return new ExtractResult(
                    true,
                    payload,
                    Long.parseLong(parts[1]),
                    Long.parseLong(parts[2]),
                    Instant.ofEpochSecond(Long.parseLong(parts[3]))
            );
        } catch (Exception e) {
            log.warn("[Watermark] 추출 실패: {}", e.getMessage());
            return ExtractResult.notFound();
        }
    }

    public String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }

    // ── 가시적 워터마크 ────────────────────────────────────

    private BufferedImage drawVisibleWatermark(BufferedImage source, Long courseId, String ownerLabel) {
        int width = source.getWidth();
        int height = source.getHeight();

        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = output.createGraphics();

        try {
            g.drawImage(source, 0, 0, null);

            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, TILE_OPACITY));
            g.setColor(Color.WHITE);

            // 이미지 크기에 비례한 폰트. 작은 이미지에서 글자가 덮어버리지 않게 상한을 둔다.
            int fontSize = Math.max(18, Math.min(48, width / 18));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, fontSize));

            String tileText = (ownerLabel == null || ownerLabel.isBlank())
                    ? "DesignMarket #" + courseId
                    : ownerLabel + " · DesignMarket #" + courseId;

            // 기울인 상태로 격자 반복 배치.
            // 회전 때문에 모서리가 비지 않도록 캔버스보다 넓은 범위를 훑는다.
            AffineTransform saved = g.getTransform();
            g.rotate(Math.toRadians(TILE_ANGLE_DEGREES), width / 2.0, height / 2.0);

            int gapX = Math.max(TILE_GAP_X, fontSize * 9);
            int gapY = Math.max(TILE_GAP_Y, fontSize * 4);

            for (int y = -height; y < height * 2; y += gapY) {
                for (int x = -width; x < width * 2; x += gapX) {
                    g.drawString(tileText, x, y);
                }
            }
            g.setTransform(saved);

            // 하단 저작권 라벨
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.65f));
            int labelSize = Math.max(12, Math.min(24, width / 40));
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, labelSize));

            String copyright = "© " + (ownerLabel == null || ownerLabel.isBlank() ? "DesignMarket" : ownerLabel);
            FontMetrics fm = g.getFontMetrics();
            int labelX = width - fm.stringWidth(copyright) - labelSize;
            int labelY = height - labelSize;

            // 밝은 배경에서도 읽히도록 어두운 그림자를 한 겹 깐다
            g.setColor(new Color(0, 0, 0, 140));
            g.drawString(copyright, labelX + 1, labelY + 1);
            g.setColor(Color.WHITE);
            g.drawString(copyright, labelX, labelY);

        } finally {
            g.dispose();
        }

        return output;
    }

    // ── 비가시적 워터마크 (LSB) ────────────────────────────

    /**
     * 파랑 채널 최하위 비트에 페이로드를 심는다.
     * 채널값이 최대 1만큼 변하므로 육안으로는 구분되지 않는다.
     */
    private BufferedImage embedInvisible(BufferedImage source, String payload) {
        byte[] data = payload.getBytes(StandardCharsets.UTF_8);
        int requiredBits = HEADER_BITS + data.length * 8;
        int capacity = source.getWidth() * source.getHeight();

        if (requiredBits > capacity) {
            throw new IllegalArgumentException(
                    "이미지가 너무 작아 워터마크를 삽입할 수 없습니다. 짧은 변이 "
                            + MIN_SHORT_EDGE + "px 이상인 이미지를 올려주세요.");
        }

        BufferedImage output = new BufferedImage(
                source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        output.getGraphics().drawImage(source, 0, 0, null);

        int bitIndex = 0;

        // 헤더: 매직 32bit + 길이 32bit
        bitIndex = writeInt(output, MAGIC, bitIndex);
        bitIndex = writeInt(output, data.length, bitIndex);

        // 본문
        for (byte b : data) {
            for (int i = 7; i >= 0; i--) {
                writeBit(output, (b >> i) & 1, bitIndex++);
            }
        }

        return output;
    }

    private String extractInvisible(BufferedImage image) {
        int capacity = image.getWidth() * image.getHeight();
        if (capacity < HEADER_BITS) return null;

        int bitIndex = 0;

        int magic = readInt(image, bitIndex);
        bitIndex += 32;
        if (magic != MAGIC) return null;

        int length = readInt(image, bitIndex);
        bitIndex += 32;

        // 손상된 파일이 말도 안 되는 길이를 주장할 수 있으므로 방어한다
        if (length <= 0 || HEADER_BITS + length * 8 > capacity || length > 4096) {
            return null;
        }

        byte[] data = new byte[length];
        for (int i = 0; i < length; i++) {
            int value = 0;
            for (int b = 0; b < 8; b++) {
                value = (value << 1) | readBit(image, bitIndex++);
            }
            data[i] = (byte) value;
        }

        return new String(data, StandardCharsets.UTF_8);
    }

    private int writeInt(BufferedImage image, int value, int bitIndex) {
        for (int i = 31; i >= 0; i--) {
            writeBit(image, (value >> i) & 1, bitIndex++);
        }
        return bitIndex;
    }

    private int readInt(BufferedImage image, int bitIndex) {
        int value = 0;
        for (int i = 0; i < 32; i++) {
            value = (value << 1) | readBit(image, bitIndex + i);
        }
        return value;
    }

    private void writeBit(BufferedImage image, int bit, int bitIndex) {
        int x = bitIndex % image.getWidth();
        int y = bitIndex / image.getWidth();

        int rgb = image.getRGB(x, y);
        int blue = (rgb & 0xFF);
        int newBlue = (blue & 0xFE) | bit;

        image.setRGB(x, y, (rgb & 0xFFFFFF00) | newBlue);
    }

    private int readBit(BufferedImage image, int bitIndex) {
        int x = bitIndex % image.getWidth();
        int y = bitIndex / image.getWidth();
        return image.getRGB(x, y) & 1;
    }

    // ── 유틸 ──────────────────────────────────────────────

    private String buildPayload(Long courseId, Long ownerId) {
        return String.join(PAYLOAD_DELIMITER,
                PAYLOAD_VERSION,
                String.valueOf(courseId),
                String.valueOf(ownerId),
                String.valueOf(Instant.now().getEpochSecond()));
    }

    private BufferedImage readImage(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                // WEBP는 기본 ImageIO에 디코더가 없다. build.gradle에 별도 의존성이 필요하다.
                throw new IllegalArgumentException(
                        "이미지를 읽을 수 없는 형식입니다. PNG 또는 JPG로 올려주세요.");
            }
            return image;
        } catch (IOException e) {
            throw new IllegalArgumentException("이미지를 읽는 중 오류가 발생했습니다.", e);
        }
    }

    private void validateSize(BufferedImage image) {
        int shortEdge = Math.min(image.getWidth(), image.getHeight());
        if (shortEdge < MIN_SHORT_EDGE) {
            throw new IllegalArgumentException(
                    "이미지의 짧은 변이 " + MIN_SHORT_EDGE + "px 이상이어야 합니다. (현재 " + shortEdge + "px)");
        }
    }

    /** 알파 채널이 있으면 LSB 계산이 어긋나므로 RGB로 통일한다 */
    private BufferedImage toRgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }
        BufferedImage rgb = new BufferedImage(
                source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            // 투명 영역은 흰색으로 채운다
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, source.getWidth(), source.getHeight());
            g.drawImage(source, 0, 0, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }

    /** 반드시 PNG로 저장한다. JPEG로 저장하면 LSB가 파괴된다. */
    private byte[] toPngBytes(BufferedImage image) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("이미지 인코딩에 실패했습니다.", e);
        }
    }
}
