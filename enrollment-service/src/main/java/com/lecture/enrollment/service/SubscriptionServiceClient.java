package com.lecture.enrollment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionServiceClient {

    private final WebClient.Builder webClientBuilder;

    /** 구독 중이면 30% 할인 적용, 아니면 원가 그대로. 장애 시 원가로 폴백(구매 흐름을 막지 않음). */
    public BigDecimal applyDiscountIfSubscribed(Long userId, Long designerId, BigDecimal price) {
        try {
            DiscountCheckResponse res = webClientBuilder.build()
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("http").host("payment-service").port(8084)
                            .path("/api/subscriptions/internal/discount")
                            .queryParam("userId", userId)
                            .queryParam("designerId", designerId)
                            .build())
                    .retrieve()
                    .bodyToMono(DiscountCheckResponse.class)
                    .block();

            if (res != null && res.isSubscribed()) {
                BigDecimal rate = BigDecimal.ONE.subtract(res.getDiscountRate());
                return price.multiply(rate).setScale(0, RoundingMode.HALF_UP);
            }
            return price;
        } catch (Exception e) {
            log.error("[SubscriptionServiceClient] 할인 확인 실패, 정가로 진행 - userId: {}, designerId: {}, error: {}",
                    userId, designerId, e.getMessage());
            return price;
        }
    }

    @lombok.Getter
    @lombok.NoArgsConstructor
    static class DiscountCheckResponse {
        private boolean subscribed;
        private BigDecimal discountRate;
    }
}