package com.lecture.course.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 구매 여부 확인을 위해 Enrollment Service를 호출한다.
 *
 * 서비스가 분리되어 있으므로 JOIN 대신 API 호출로 데이터를 조합한다.
 * 호출 실패 시 예외를 전파하지 않고 '구매하지 않음'으로 처리한다 —
 * 다른 서비스의 장애가 이 서비스의 오류로 번지지 않게 하기 위함이다.
 */
@Slf4j
@Component
public class EnrollmentClient {

    private final WebClient webClient;

    public EnrollmentClient(WebClient.Builder builder,
                            @Value("${service.enrollment-service.url}") String baseUrl) {
        this.webClient = builder.baseUrl(baseUrl).build();
    }

    @SuppressWarnings("unchecked")
    public boolean hasPurchased(Long userId, Long courseId) {
        try {
            Map<String, Object> body = webClient.get()
                    .uri("/api/enrollments/internal/history/{userId}", userId)
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
            log.warn("구매 이력 조회 실패 - userId: {}, courseId: {}, error: {}",
                    userId, courseId, e.toString());
            return false;
        }
    }
}
