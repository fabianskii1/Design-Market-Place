package com.lecture.course.watermark;

import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * 비가시적 워터마크 — LSB(Least Significant Bit) 스테가노그래피.
 *
 * 각 픽셀의 파랑 채널 최하위 1비트에 페이로드를 순서대로 심는다.
 * 파랑 채널 값이 최대 1만큼 달라지므로 육안으로는 구분되지 않는다.
 *
 * 비트 배치:
 *   [0..31]  매직 넘버 "DMPW"  — 워터마크 존재 여부 판별
 *   [32..63] 페이로드 길이(int, big-endian)
 *   [64.. ]  UTF-8 페이로드
 *
 * 주의: JPEG처럼 손실 압축을 거치면 최하위 비트가 파괴되어 추출이 불가능하다.
 *      따라서 삽입 결과는 반드시 PNG 등 무손실 포맷으로 저장해야 한다.
 */
@Component
public class LsbWatermarker {

    private static final byte[] MAGIC = "DMPW".getBytes(StandardCharsets.US_ASCII);
    private static final int HEADER_BYTES = MAGIC.length + 4;
    private static final int MAX_PAYLOAD_BYTES = 512;

    /** 이 이미지에 최대 몇 바이트를 심을 수 있는지 */
    public int capacityBytes(BufferedImage image) {
        return (image.getWidth() * image.getHeight()) / 8 - HEADER_BYTES;
    }

    public BufferedImage embed(BufferedImage source, String payload) {
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        if (payloadBytes.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("워터마크 페이로드가 너무 깁니다: " + payloadBytes.length + " bytes");
        }
        if (payloadBytes.length > capacityBytes(source)) {
            throw new IllegalArgumentException(
                    "이미지가 너무 작아 워터마크를 삽입할 수 없습니다. 최소 "
                            + ((HEADER_BYTES + payloadBytes.length) * 8) + "픽셀 필요");
        }

        byte[] frame = new byte[HEADER_BYTES + payloadBytes.length];
        System.arraycopy(MAGIC, 0, frame, 0, MAGIC.length);
        int len = payloadBytes.length;
        frame[4] = (byte) (len >>> 24);
        frame[5] = (byte) (len >>> 16);
        frame[6] = (byte) (len >>> 8);
        frame[7] = (byte) len;
        System.arraycopy(payloadBytes, 0, frame, HEADER_BYTES, len);

        int width = source.getWidth();
        int height = source.getHeight();
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        out.getGraphics().drawImage(source, 0, 0, null);

        int totalBits = frame.length * 8;
        int bitIndex = 0;
        outer:
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (bitIndex >= totalBits) break outer;
                int bit = (frame[bitIndex >> 3] >> (7 - (bitIndex & 7))) & 1;
                int rgb = out.getRGB(x, y);
                out.setRGB(x, y, (rgb & 0xFFFFFFFE) | bit);
                bitIndex++;
            }
        }
        return out;
    }

    /** 워터마크가 없거나 손상된 경우 Optional.empty() */
    public Optional<String> extract(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        long available = (long) width * height;
        if (available < (long) HEADER_BYTES * 8) return Optional.empty();

        byte[] header = readBytes(image, 0, HEADER_BYTES);
        for (int i = 0; i < MAGIC.length; i++) {
            if (header[i] != MAGIC[i]) return Optional.empty();
        }

        int len = ((header[4] & 0xFF) << 24)
                | ((header[5] & 0xFF) << 16)
                | ((header[6] & 0xFF) << 8)
                | (header[7] & 0xFF);
        if (len <= 0 || len > MAX_PAYLOAD_BYTES) return Optional.empty();
        if ((long) (HEADER_BYTES + len) * 8 > available) return Optional.empty();

        byte[] payload = readBytes(image, HEADER_BYTES * 8, len);
        return Optional.of(new String(payload, StandardCharsets.UTF_8));
    }

    private byte[] readBytes(BufferedImage image, int startBit, int byteCount) {
        byte[] buf = new byte[byteCount];
        int width = image.getWidth();
        for (int i = 0; i < byteCount * 8; i++) {
            int globalBit = startBit + i;
            int x = globalBit % width;
            int y = globalBit / width;
            int bit = image.getRGB(x, y) & 1;
            buf[i >> 3] |= (byte) (bit << (7 - (i & 7)));
        }
        return buf;
    }
}
