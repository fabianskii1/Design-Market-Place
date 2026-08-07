package com.lecture.course.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * enrollment-service 조회 클라이언트.
 *
 * 원본 다운로드 권한 판정에만 쓴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EnrollmentServiceClient {

    private final WebClient.Builder webClientBuilder;

    /**
     * 해당 사용자가 이 디자인을 구매(ACTIVE)했는지.
     *
     * 통신 실패 시 false를 돌려준다. 확인이 안 되는 상황에서
     * 원본을 내주는 쪽으로 기울면 유료 자산이 새어나가므로,
     * 막는 쪽을 기본값으로 둔다.
     */
    public boolean hasPurchased(Long userId, Long courseId) {
        try {
            Boolean result = webClientBuilder.build()
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("http")
                            .host("enrollment-service")
                            .port(8083)
                            .path("/api/enrollments/internal/exists")
                            .queryParam("userId", userId)
                            .queryParam("courseId", courseId)
                            .build())
                    .retrieve()
                    .bodyToMono(Boolean.class)
                    .timeout(Duration.ofSeconds(3))
                    .block();

            return Boolean.TRUE.equals(result);

        } catch (Exception e) {
            log.error("[EnrollmentServiceClient] 구매 여부 조회 실패 userId={} courseId={}: {}",
                    userId, courseId, e.getMessage());
            return false;
        }
    }
}
