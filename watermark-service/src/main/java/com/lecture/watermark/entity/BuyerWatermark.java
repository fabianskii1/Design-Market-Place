package com.lecture.watermark.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 구매자별 워터마크 사본 이력.
 *
 * 유출본에서 추출한 페이로드로 여기를 조회하면
 * 어떤 구매 건에서 새어나간 파일인지 특정할 수 있다.
 */
@Entity
@Table(
    name = "buyer_watermarks",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_buyer_watermark", columnNames = {"course_id", "buyer_id"})
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class BuyerWatermark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    /** 저장된 파일명 */
    @Column(nullable = false, length = 255)
    private String storedName;

    /** 이미지에 심긴 페이로드 원문 */
    @Column(nullable = false, length = 255)
    private String payload;

    /** 사본의 SHA-256. 유출본이 재가공됐는지 판별한다 */
    @Column(nullable = false, length = 64)
    private String checksum;

    @Column(nullable = false)
    private Long fileSize;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    public void replace(String storedName, String payload, String checksum, Long fileSize) {
        this.storedName = storedName;
        this.payload = payload;
        this.checksum = checksum;
        this.fileSize = fileSize;
    }
}
