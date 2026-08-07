package com.lecture.course.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * 워터마크 처리를 watermark-service 에 위임한다.
 *
 * 이미지 가공을 별도 서비스로 분리한 이유:
 *  1. 워터마크는 CPU를 오래 쓰는 작업이라, 조회 트래픽을 받는 course-service와
 *     같은 인스턴스에서 돌면 응답 지연이 그대로 전파된다.
 *  2. 구매 확정(purchase.completed)에 반응해 구매자별 사본을 만드는 일은
 *     course-service의 책임이 아니다.
 */
@Slf4j
@Component
public class WatermarkClient {

    private final WebClient webClient;

    public WatermarkClient(WebClient.Builder builder,
                           @Value("${service.watermark-service.url}") String baseUrl) {
        this.webClient = builder
                .baseUrl(baseUrl)
                // 이미지 바이트를 주고받으므로 기본 256KB 제한을 올린다
                .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }

    /** 미리보기본 (가시적 + 비가시적) */
    public byte[] createPreview(byte[] image, Long courseId, Long ownerId, String ownerLabel) {
        return post("/api/watermark/internal/preview", image, courseId, ownerId, ownerLabel);
    }

    /** 판매용 원본 (비가시적만) */
    public byte[] createOriginal(byte[] image, Long courseId, Long ownerId) {
        return post("/api/watermark/internal/original", image, courseId, ownerId, null);
    }

    /**
     * 구매자 전용 사본.
     * 아직 생성 전이면 empty 를 돌려주고, 호출 측이 원본으로 폴백한다.
     * 이벤트 처리가 늦어져도 구매자가 다운로드를 못 하는 상황은 만들지 않는다.
     */
    public Optional<byte[]> findBuyerCopy(Long courseId, Long buyerId) {
        try {
            byte[] bytes = webClient.get()
                    .uri("/api/watermark/internal/buyer-copy/{courseId}/{buyerId}", courseId, buyerId)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            return Optional.ofNullable(bytes);
        } catch (Exception e) {
            log.info("[WatermarkClient] 구매자 사본 없음 courseId={} buyerId={} ({})",
                    courseId, buyerId, e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /** 유출본 추적 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> trace(byte[] image) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("file", asResource(image)).filename("upload.png");

        return webClient.post()
                .uri("/api/watermark/internal/trace")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(body.build()))
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(30))
                .block();
    }

    private byte[] post(String path, byte[] image, Long courseId, Long ownerId, String ownerLabel) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("file", asResource(image)).filename("upload.png");

        byte[] result = webClient.post()
                .uri(uri -> {
                    var b = uri.path(path)
                            .queryParam("courseId", courseId)
                            .queryParam("ownerId", ownerId);
                    if (ownerLabel != null && !ownerLabel.isBlank()) {
                        b.queryParam("ownerLabel", ownerLabel);
                    }
                    return b.build();
                })
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(body.build()))
                .retrieve()
                .bodyToMono(byte[].class)
                .timeout(Duration.ofSeconds(60))
                .block();

        if (result == null || result.length == 0) {
            throw new IllegalStateException("워터마크 처리 결과가 비어 있습니다.");
        }
        return result;
    }

    private ByteArrayResource asResource(byte[] bytes) {
        return new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return "upload.png";
            }
        };
    }
}
