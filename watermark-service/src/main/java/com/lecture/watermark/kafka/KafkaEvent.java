package com.lecture.watermark.kafka;

import lombok.*;

public class KafkaEvent {

    /**
     * Enrollment Service → Watermark Service
     *
     * 결제가 끝나고 구매가 ACTIVE 로 확정된 시점에 발행된다.
     * 이 시점에야 "누가 샀는지"가 정해지므로, 구매자별 워터마크는 여기서 만든다.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @ToString
    public static class PurchaseCompletedEvent {
        private Long purchaseId;
        private Long buyerId;
        private Long courseId;
        private Long occurredAt;   // epoch seconds
    }
}
