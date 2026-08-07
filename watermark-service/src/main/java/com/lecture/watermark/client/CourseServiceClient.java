package com.lecture.watermark.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * 원본 이미지를 course-service 에서 가져온다.
 *
 * 파일시스템을 공유하지 않고 HTTP 로 주고받는다.
 * 서비스마다 자기 저장소를 갖고, 필요한 데이터는 API 로 요청하는 구조를 유지하기 위함이다.
 */
@Slf4j
@Component
public class CourseServiceClient {

    private final WebClient webClient;

    public CourseServiceClient(WebClient.Builder builder,
                               @Value("${service.course-service.url}") String baseUrl) {
        this.webClient = builder
                .baseUrl(baseUrl)
                // 이미지 바이트를 그대로 받으므로 기본 256KB 제한을 올린다
                .codecs(c -> c.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }

    /** 판매용 원본(비가시적 워터마크만 심긴 파일) 바이트 */
    public byte[] fetchOriginal(Long courseId) {
        return webClient.get()
                .uri("/api/courses/internal/{id}/asset/original", courseId)
                .retrieve()
                .bodyToMono(byte[].class)
                .timeout(Duration.ofSeconds(20))
                .block();
    }

    /** 워터마크에 찍을 판매자 ID */
    public Long fetchOwnerId(Long courseId) {
        try {
            var body = webClient.get()
                    .uri("/api/courses/internal/{id}", courseId)
                    .retrieve()
                    .bodyToMono(java.util.Map.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();

            Object ownerId = body == null ? null : body.get("instructorId");
            return ownerId instanceof Number n ? n.longValue() : null;
        } catch (Exception e) {
            log.warn("[CourseClient] 판매자 조회 실패 courseId={} : {}", courseId, e.toString());
            return null;
        }
    }
}
