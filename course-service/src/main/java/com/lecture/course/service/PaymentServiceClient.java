package com.lecture.course.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentServiceClient {

    private final WebClient.Builder webClientBuilder;

    /**
     * Payment Service: 강의별 판매 집계 조회 (판매 대시보드용)
     * 반환: courseId -> {salesCount, totalRevenue}
     */
    public Map<Long, CourseSales> getSalesSummary(List<Long> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) {
            return Map.of();
        }
        try {
            List<Map<String, Object>> raw = webClientBuilder.build()
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("http")
                            .host("payment-service")
                            .port(8084)
                            .path("/api/payments/internal/sales")
                            .queryParam("courseIds", courseIds)
                            .build())
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                    .block();

            Map<Long, CourseSales> result = new HashMap<>();
            if (raw != null) {
                for (Map<String, Object> row : raw) {
                    Long courseId = ((Number) row.get("courseId")).longValue();
                    Long count = ((Number) row.get("salesCount")).longValue();
                    BigDecimal revenue = new BigDecimal(row.get("totalRevenue").toString());
                    result.put(courseId, new CourseSales(count, revenue));
                }
            }
            return result;
        } catch (Exception e) {
            log.error("[PaymentServiceClient] 판매 집계 조회 실패: {}", e.getMessage());
            return Map.of();
        }
    }

    public record CourseSales(Long salesCount, BigDecimal totalRevenue) {}
}