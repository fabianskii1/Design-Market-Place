package com.lecture.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicInsert;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @DynamicInsert: courseId/licenseTierId가 null인 구독 결제를 저장할 때
 * INSERT 문에서 그 컬럼들을 아예 빼도록 한다. (null을 명시적으로 바인딩하는 방식에서
 * "Field ... doesn't have a default value" 류의 원인 불명 오류가 반복 발생해 우회함)
 */
@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@DynamicInsert
@EntityListeners(AuditingEntityListener.class)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "course_id") // 기존 nullable = false 제거 (구독 결제는 courseId 없음)
    private Long courseId;

    @Column(name = "license_tier_id")
    private Long licenseTierId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", length = 20)
    @Builder.Default
    private PaymentType paymentType = PaymentType.ENROLLMENT;

    public enum PaymentType { ENROLLMENT, SUBSCRIPTION }

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Status status = Status.PENDING;

    // 외부 PG사 거래 ID (실습에서는 UUID로 대체)
    @Column(name = "transaction_id", unique = true)
    private String transactionId;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public enum Status {
        PENDING,    // 결제 대기
        COMPLETED,  // 결제 완료
        FAILED,     // 결제 실패
        CANCELLED   // 취소
    }

    public void complete(String transactionId) {
        this.status = Status.COMPLETED;
        this.transactionId = transactionId;
    }

    public void fail() {
        this.status = Status.FAILED;
    }
}
