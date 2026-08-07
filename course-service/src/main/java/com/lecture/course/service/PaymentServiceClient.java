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

    /**
     * Payment Service: 강의별 월별 매출 (monthlyBreakdown 계산용)
     * yearMonth는 payment-service가 DB DATE_FORMAT('%Y-%m')로 이미 "YYYY-MM" 포맷을 보장해서 내려준다.
     */
    public List<MonthlySales> getMonthlySales(List<Long> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> raw = webClientBuilder.build()
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("http")
                            .host("payment-service")
                            .port(8084)
                            .path("/api/payments/internal/sales/monthly")
                            .queryParam("courseIds", courseIds)
                            .build())
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                    .block();

            List<MonthlySales> result = new ArrayList<>();
            if (raw != null) {
                for (Map<String, Object> row : raw) {
                    result.add(new MonthlySales(
                            ((Number) row.get("courseId")).longValue(),
                            (String) row.get("yearMonth"),
                            new BigDecimal(row.get("revenue").toString()),
                            ((Number) row.get("salesCount")).longValue()));
                }
            }
            return result;
        } catch (Exception e) {
            log.error("[PaymentServiceClient] 월별 매출 조회 실패: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Payment Service: 강의별 · 라이선스 등급별 판매 집계 (salesByLicense 계산용)
     */
    public List<LicenseTierSales> getLicenseTierSales(List<Long> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> raw = webClientBuilder.build()
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("http")
                            .host("payment-service")
                            .port(8084)
                            .path("/api/payments/internal/sales/by-license-tier")
                            .queryParam("courseIds", courseIds)
                            .build())
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
                    .block();

            List<LicenseTierSales> result = new ArrayList<>();
            if (raw != null) {
                for (Map<String, Object> row : raw) {
                    Object tierIdObj = row.get("licenseTierId");
                    result.add(new LicenseTierSales(
                            ((Number) row.get("courseId")).longValue(),
                            tierIdObj == null ? null : ((Number) tierIdObj).longValue(),
                            ((Number) row.get("salesCount")).longValue(),
                            new BigDecimal(row.get("revenue").toString())));
                }
            }
            return result;
        } catch (Exception e) {
            log.error("[PaymentServiceClient] 라이선스 등급별 판매 집계 조회 실패: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Payment Service: 디자이너의 활성 구독자 수
     */
    public Long getSubscriberCount(Long instructorId) {
        try {
            Long count = webClientBuilder.build()
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("http")
                            .host("payment-service")
                            .port(8084)
                            .path("/api/payments/subscriptions/internal/subscriber-count/{id}")
                            .build(instructorId))
                    .retrieve()
                    .bodyToMono(Long.class)
                    .block();
            return count != null ? count : 0L;
        } catch (Exception e) {
            log.error("[PaymentServiceClient] 구독자 수 조회 실패: {}", e.getMessage());
            return 0L;
        }
    }

    public record MonthlySales(Long courseId, String yearMonth, BigDecimal revenue, Long salesCount) {}
    public record LicenseTierSales(Long courseId, Long licenseTierId, Long salesCount, BigDecimal revenue) {}
}