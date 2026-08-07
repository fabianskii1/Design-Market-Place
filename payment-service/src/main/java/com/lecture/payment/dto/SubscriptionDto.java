package com.lecture.payment.dto;

import com.lecture.payment.entity.Subscription;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class SubscriptionDto {

    @Getter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SubscribeRequest {
        @NotNull(message = "구독할 디자이너 ID는 필수입니다")
        private Long designerId;
    }

    @Getter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SubscriptionResponse {
        private Long id;
        private Long userId;
        private Long designerId;
        private Subscription.Status status;
        private BigDecimal monthlyPrice;
        private LocalDateTime startedAt;
        private LocalDateTime currentPeriodEnd;
        private Boolean autoRenew;

        public static SubscriptionResponse from(Subscription s) {
            return SubscriptionResponse.builder()
                    .id(s.getId()).userId(s.getUserId()).designerId(s.getDesignerId())
                    .status(s.getStatus()).monthlyPrice(s.getMonthlyPrice())
                    .startedAt(s.getStartedAt())
                    .currentPeriodEnd(s.getCurrentPeriodEnd()).autoRenew(s.getAutoRenew())
                    .build();
        }
    }

    @Getter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DiscountCheckResponse {
        private boolean subscribed;
        private BigDecimal discountRate;
    }

    @Getter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ApiResponse<T> {
        private boolean success;
        private String message;
        private T data;

        public static <T> ApiResponse<T> success(T data) {
            return ApiResponse.<T>builder().success(true).message("성공").data(data).build();
        }
        public static <T> ApiResponse<T> error(String message) {
            return ApiResponse.<T>builder().success(false).message(message).build();
        }
    }
}