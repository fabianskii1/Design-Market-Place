package com.lecture.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 구독 (기간제).
 *
 * 구매(Enrollment)와 분리한 이유: 구매는 한 번 성립하면 영구하지만
 * 구독은 기간·갱신·해지라는 상태 전이를 갖는다.
 * 한 테이블에 담으면 절반이 NULL 컬럼이 된다.
 */
@Entity
@Table(name = "subscriptions")
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
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_code", nullable = false, length = 30)
    private PlanCode planCode;

    /** 구독 다운로드에 적용될 라이선스 등급 (license_tiers.id) */
    @Column(name = "license_tier_id", nullable = false)
    private Long licenseTierId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.ACTIVE;

    @Column(name = "monthly_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal monthlyPrice;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    /** 현재 주기 종료 시각. 이 시점 이후 갱신 또는 만료 처리된다. */
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

    public enum PlanCode {
        BASIC, PRO
    }

    public enum Status {
        ACTIVE,     // 구독 중
        CANCELLED,  // 해지 예약 (주기 종료까지는 유효)
        EXPIRED     // 만료
    }

    /**
     * 지금 시점에 구독 혜택을 받을 수 있는지.
     * 해지 예약(CANCELLED) 상태라도 현재 주기가 남아 있으면 혜택은 유지된다.
     */
    public boolean isBenefitActive() {
        return status != Status.EXPIRED
                && currentPeriodEnd.isAfter(LocalDateTime.now());
    }

    /** 정기 결제 성공 시 다음 주기로 연장 */
    public void renew(LocalDateTime nextPeriodEnd) {
        this.currentPeriodEnd = nextPeriodEnd;
        this.status = Status.ACTIVE;
    }

    /** 해지 예약 - 현재 주기까지는 유효하다 */
    public void cancel() {
        this.status = Status.CANCELLED;
        this.autoRenew = false;
        this.cancelledAt = LocalDateTime.now();
    }

    /** 주기 종료 후 만료 처리 (스케줄러) */
    public void expire() {
        this.status = Status.EXPIRED;
        this.autoRenew = false;
    }
}