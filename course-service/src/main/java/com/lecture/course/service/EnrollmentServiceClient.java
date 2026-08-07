package com.lecture.course.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

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
     * enrollment-service 가 제공하는 것은 사용자별 구매 목록뿐이라
     * 목록을 받아 포함 여부를 확인한다.
     * 구매 건수가 많아지면 단건 조회 엔드포인트로 바꾸는 편이 낫다.
     *
     * 통신 실패 시 false 를 돌려준다. 확인이 안 되는 상황에서 원본을 내주면
     * 유료 자산이 새어나가므로, 막는 쪽을 기본값으로 둔다.
     */
    @SuppressWarnings("unchecked")
    public boolean hasPurchased(Long userId, Long courseId) {
        try {
            Map<String, Object> body = webClientBuilder.build()
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("http")
                            .host("enrollment-service")
                            .port(8083)
                            .path("/api/enrollments/internal/history/{userId}")
                            .build(userId))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(3))
                    .block();

            if (body == null) return false;

            Object ids = body.get("activeCourseIds");
            if (!(ids instanceof List<?> list)) return false;

            return list.stream()
                    .filter(v -> v instanceof Number)
                    .anyMatch(v -> ((Number) v).longValue() == courseId);

        } catch (Exception e) {
            log.error("[EnrollmentServiceClient] 구매 여부 조회 실패 userId={} courseId={}: {}",
                    userId, courseId, e.getMessage());
            return false;
        }
    }
}
