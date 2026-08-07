package com.lecture.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 디자이너별 구독 (기간제, 1개월 고정).
 * 구독자가 특정 디자이너를 구독하면 그 디자이너의 모든 디자인·모든 라이선스 등급에
 * 30% 할인이 구매 시점에 적용된다. 할인율은 이 엔티티가 아니라
 * SubscriptionService의 설정값(subscription.discount-rate)이 단일 진실 공급원이다.
 */
@Entity
@Table(name = "subscriptions",
       uniqueConstraints = @UniqueConstraint(
               name = "uq_subscriber_designer",
               columnNames = {"user_id", "designer_id"}))
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId; // 구독자

    @Column(name = "designer_id", nullable = false)
    private Long designerId; // 구독 대상 디자이너 (courses.instructor_id)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.ACTIVE;

    @Column(name = "monthly_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal monthlyPrice;

    // discount_rate는 컬럼으로 안 둔다. 일괄 30% 고정값이라 row마다 들고 있을 이유가 없고,
    // 나중에 정책이 바뀌면 SubscriptionService의 상수/설정값 하나만 고치면 되게 한다.

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "current_period_end", nullable = false)
    private LocalDateTime currentPeriodEnd;

    @Column(name = "auto_renew", nullable = false)
    @Builder.Default
    private Boolean autoRenew = true;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public enum Status { ACTIVE, CANCELLED, EXPIRED }

    public static Subscription start(Long userId, Long designerId, BigDecimal monthlyPrice) {
        LocalDateTime now = LocalDateTime.now();
        return Subscription.builder()
                .userId(userId)
                .designerId(designerId)
                .monthlyPrice(monthlyPrice)
                .startedAt(now)
                .currentPeriodEnd(now.plusMonths(1))
                .build();
    }

    /** 해지 예약 상태여도 currentPeriodEnd 전이면 혜택은 유지 */
    public boolean isBenefitActive() {
        return status != Status.EXPIRED && currentPeriodEnd.isAfter(LocalDateTime.now());
    }

    public void renew() {
        this.currentPeriodEnd = this.currentPeriodEnd.plusMonths(1);
        this.status = Status.ACTIVE;
    }

    public void cancel() {
        this.status = Status.CANCELLED;
        this.autoRenew = false;
        this.cancelledAt = LocalDateTime.now();
    }

    public void expire() {
        this.status = Status.EXPIRED;
        this.autoRenew = false;
    }
}