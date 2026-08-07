package com.lecture.watermark.kafka;

import com.lecture.watermark.service.BuyerWatermarkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * purchase.completed 수신 → 구매자별 워터마크 사본 생성.
 *
 * enrollment-service 가 JsonSerializer 로 타입 헤더 없이 발행하므로
 * 특정 DTO 로 바로 받지 않고 Map 으로 받아 방어적으로 파싱한다.
 * (기존 payment.completed 컨슈머와 같은 방식)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseCompletedConsumer {

    private final BuyerWatermarkService buyerWatermarkService;

    @KafkaListener(
            topics = "${kafka.topic.purchase-completed}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void handle(Map<String, Object> event) {
        log.info("[Kafka Consumer] purchase.completed 수신: {}", event);

        try {
            Long courseId = asLong(event.get("courseId"));
            Long buyerId = asLong(event.get("buyerId") != null ? event.get("buyerId") : event.get("userId"));

            if (courseId == null || buyerId == null) {
                throw new IllegalArgumentException("courseId 또는 buyerId가 없습니다: " + event);
            }

            buyerWatermarkService.createForPurchase(courseId, buyerId);

        } catch (Exception e) {
            // 예외를 던지면 무한 재시도에 빠진다. 기록만 남기고 오프셋을 넘긴다.
            // 실패한 구매 건은 다운로드 시점에 원본으로 폴백되므로 서비스는 계속 동작한다.
            log.error("[Kafka Consumer] 구매자 사본 생성 실패 - event: {}, error: {}",
                    event, e.getMessage(), e);
        }
    }

    private Long asLong(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }
}
