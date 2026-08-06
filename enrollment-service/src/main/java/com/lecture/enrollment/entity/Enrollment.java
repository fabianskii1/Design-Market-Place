package com.lecture.enrollment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 구매 내역.
 * 테이블명은 기존 enrollments 를 그대로 재사용한다.
 *
 * 구매한 라이선스 등급과 지불 금액을 스냅샷으로 남겨
 * 등급 정책이나 가격이 나중에 바뀌어도 과거 구매자의 권리를 보존한다.
 */
@Entity
@Table(name = "enrollments",
       uniqueConstraints = @UniqueConstraint(
               name = "uq_user_course",
               columnNames = {"user_id", "course_id"}))
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 디자인 ID (courses.id) */
    @Column(name = "course_id", nullable = false)
    private Long courseId;

    /** 구매한 라이선스 등급 (license_tiers.id) - 구매 시점 고정 */
    @Column(name = "license_tier_id")
    private Long licenseTierId;

    /** 구매 시점의 지불 금액 */
    @Column(name = "paid_amount", precision = 10, scale = 2)
    private BigDecimal paidAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.PENDING;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public enum Status {
        PENDING,   // 구매 신청 완료, 결제 대기
        ACTIVE,    // 결제 완료, 다운로드 가능
        CANCELLED  // 취소
    }

    public static Enrollment create(Long userId, Long courseId,
                                    Long licenseTierId, BigDecimal paidAmount) {
        return Enrollment.builder()
                .userId(userId)
                .courseId(courseId)
                .licenseTierId(licenseTierId)
                .paidAmount(paidAmount)
                .status(Status.PENDING)
                .build();
    }

    public void activate() {
        this.status = Status.ACTIVE;
    }

    public void cancel() {
        this.status = Status.CANCELLED;
    }
}