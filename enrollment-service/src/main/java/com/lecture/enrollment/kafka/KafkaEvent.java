package com.lecture.enrollment.kafka;

import lombok.*;

/**
 * Kafka 이벤트 메시지 DTO
 */
public class KafkaEvent {

    /**
     * Payment Service → Enrollment Service
     * 결제 완료 이벤트 수신
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PaymentCompletedEvent {
        private Long paymentId;
        private Long userId;
        private Long courseId;
        private String status; // COMPLETED
    }

    /**
     * Enrollment Service → Recommend Service
     * 수강 활성화 완료 이벤트 발행
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EnrollmentCompletedEvent {
        private Long enrollmentId;
        private Long userId;
        private Long courseId;
    }

    /**
     * Enrollment Service → Watermark Service
     * 구매 확정 이벤트 발행
     *
     * 결제가 끝나고 구매가 ACTIVE 로 확정된 시점에만 발행한다.
     * 이때야 "누가 샀는지"가 정해지므로, 구매자별 워터마크는 이 이벤트를 기점으로 만들어진다.
     * userId 가 아니라 buyerId 로 이름 붙인 것은 수신 측에서 의미가 분명하도록 하기 위함이다.
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PurchaseCompletedEvent {
        private Long purchaseId;
        private Long buyerId;
        private Long courseId;
        private Long occurredAt;   // epoch seconds
    }
}
