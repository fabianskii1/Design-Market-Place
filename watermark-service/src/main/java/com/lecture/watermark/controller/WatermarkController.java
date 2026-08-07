package com.lecture.watermark.controller;

import com.lecture.watermark.dto.WatermarkDto;
import com.lecture.watermark.service.BuyerWatermarkService;
import com.lecture.watermark.service.WatermarkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 워터마크 처리 API.
 *
 * 경로를 /api/watermark/internal/** 로 둔 이유:
 * API Gateway 라우팅 규칙이 사전 빌드된 이미지에 고정되어 있어 새 프리픽스를 추가할 수 없다.
 * 이 서비스는 외부에 직접 노출되지 않고 Kafka 와 서비스 간 호출로만 동작하므로
 * 게이트웨이를 거칠 필요가 없다.
 */
@Slf4j
@RestController
@RequestMapping("/api/watermark/internal")
@RequiredArgsConstructor
public class WatermarkController {

    private final WatermarkService watermarkService;
    private final BuyerWatermarkService buyerWatermarkService;

    /**
     * 업로드 시점 처리 — 미리보기(가시적 + 비가시적) 생성.
     * course-service 가 파일을 올려 결과 바이트를 받아간다.
     */
    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> preview(
            @RequestPart("file") MultipartFile file,
            @RequestParam Long courseId,
            @RequestParam Long ownerId,
            @RequestParam(required = false) String ownerLabel) throws IOException {

        WatermarkService.WatermarkResult result =
                watermarkService.process(file.getBytes(), courseId, ownerId, ownerLabel);

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(result.previewBytes());
    }

    /**
     * 업로드 시점 처리 — 판매용 원본(비가시적만) 생성.
     */
    @PostMapping(value = "/original", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> original(
            @RequestPart("file") MultipartFile file,
            @RequestParam Long courseId,
            @RequestParam Long ownerId) throws IOException {

        WatermarkService.WatermarkResult result =
                watermarkService.process(file.getBytes(), courseId, ownerId, null);

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header("X-Watermark-Payload", result.payload())
                .header("X-Watermark-Checksum", result.checksum())
                .body(result.originalBytes());
    }

    /**
     * 구매자 사본 조회. 아직 생성 전이면 404.
     * course-service 가 다운로드 요청을 받았을 때 이 값을 우선 사용한다.
     */
    @GetMapping("/buyer-copy/{courseId}/{buyerId}")
    public ResponseEntity<byte[]> buyerCopy(@PathVariable Long courseId,
                                            @PathVariable Long buyerId) {
        return buyerWatermarkService.loadBuyerCopy(courseId, buyerId)
                .map(bytes -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_PNG)
                        .body(bytes))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * 유출본 추적.
     *
     * 이미지에서 워터마크를 꺼내고, 구매 이력과 대조해
     * 어떤 구매 건에서 새어나갔는지 판정한다.
     */
    @PostMapping(value = "/trace", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<WatermarkDto.TraceResponse> trace(
            @RequestPart("file") MultipartFile file) throws IOException {

        byte[] bytes = file.getBytes();
        WatermarkService.ExtractResult extracted = watermarkService.extract(bytes);

        if (!extracted.found()) {
            return ResponseEntity.ok(WatermarkDto.TraceResponse.builder()
                    .watermarkFound(false)
                    .traced(false)
                    .message("워터마크를 찾을 수 없습니다. 손실 압축·리사이즈 과정에서 훼손되었을 수 있습니다.")
                    .build());
        }

        var traced = buyerWatermarkService.traceByPayload(extracted.payload());
        String uploadedChecksum = watermarkService.sha256(bytes);

        return ResponseEntity.ok(WatermarkDto.TraceResponse.builder()
                .watermarkFound(true)
                .payload(extracted.payload())
                .courseId(extracted.courseId())
                .ownerId(extracted.ownerId())
                .buyerId(extracted.buyerId())
                .issuedAt(extracted.issuedAt() == null ? null
                        : LocalDateTime.ofInstant(extracted.issuedAt(), ZoneId.systemDefault()))
                .traced(traced.isPresent())
                .checksumMatched(traced.map(t -> t.getChecksum().equals(uploadedChecksum)).orElse(false))
                .message(buildMessage(extracted, traced.isPresent()))
                .build());
    }

    private String buildMessage(WatermarkService.ExtractResult extracted, boolean traced) {
        if (extracted.buyerId() == null) {
            return "업로드 시점에 생성된 파일입니다. 구매자 정보가 없어 유출 경로를 특정할 수 없습니다.";
        }
        return traced
                ? "구매자 " + extracted.buyerId() + " 에게 배포된 사본입니다."
                : "구매자 정보는 읽었으나 일치하는 구매 이력이 없습니다. 삭제되었거나 다른 환경에서 발급된 파일입니다.";
    }
}
