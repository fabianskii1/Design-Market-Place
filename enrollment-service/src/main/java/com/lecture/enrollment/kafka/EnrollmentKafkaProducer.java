package com.lecture.enrollment.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EnrollmentKafkaProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topic.enrollment-completed}")
    private String enrollmentCompletedTopic;

    @Value("${kafka.topic.purchase-completed}")
    private String purchaseCompletedTopic;

    /**
     * enrollment.completed 이벤트 발행
     * → Recommend Service가 수신하여 추천 갱신
     */
    public void publishEnrollmentCompleted(KafkaEvent.EnrollmentCompletedEvent event) {
        log.info("[Kafka Producer] enrollment.completed 발행 - enrollmentId: {}, userId: {}, courseId: {}",
                event.getEnrollmentId(), event.getUserId(), event.getCourseId());

        kafkaTemplate.send(enrollmentCompletedTopic, String.valueOf(event.getUserId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[Kafka Producer] enrollment.completed 발행 실패: {}", ex.getMessage());
                    } else {
                        log.info("[Kafka Producer] enrollment.completed 발행 성공 - offset: {}",
                                result.getRecordMetadata().offset());
                    }
                });
    }

    /**
     * purchase.completed 이벤트 발행
     * → Watermark Service가 수신하여 구매자별 워터마크 사본 생성
     *
     * 파티션 키를 courseId 로 잡았다. 같은 디자인에 대한 이벤트가 한 파티션에 모여
     * 순서가 보장되므로, 동시 구매 시 같은 원본을 중복 처리하는 상황을 줄일 수 있다.
     */
    public void publishPurchaseCompleted(KafkaEvent.PurchaseCompletedEvent event) {
        log.info("[Kafka Producer] purchase.completed 발행 - purchaseId: {}, buyerId: {}, courseId: {}",
                event.getPurchaseId(), event.getBuyerId(), event.getCourseId());

        kafkaTemplate.send(purchaseCompletedTopic, String.valueOf(event.getCourseId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[Kafka Producer] purchase.completed 발행 실패: {}", ex.getMessage());
                    } else {
                        log.info("[Kafka Producer] purchase.completed 발행 성공 - offset: {}",
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
