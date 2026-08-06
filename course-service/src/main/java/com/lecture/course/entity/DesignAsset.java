package com.lecture.course.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 디자인 원본 자산.
 *
 * 저장 전략: 이미지 바이너리를 MariaDB LONGBLOB에 직접 보관한다.
 *  - watermarkedData : LSB(비가시적) 워터마크가 삽입된 판매용 원본. 무손실 PNG.
 *  - previewData     : 가시적 워터마크가 얹힌 저해상도 미리보기. 비로그인 사용자에게도 공개.
 *
 * 원본(무워터마크)은 보관하지 않는다. 유출 시 추적이 불가능한 사본을 남기지 않기 위함이며,
 * 판매본에는 항상 구매 추적용 식별자가 심겨 있는 상태를 유지한다.
 */
@Entity
@Table(
    name = "design_assets",
    uniqueConstraints = @UniqueConstraint(name = "uq_design_asset_course", columnNames = "course_id"),
    indexes = @Index(name = "idx_design_asset_token", columnList = "asset_token")
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class DesignAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 대상 디자인(courses.id). FK를 걸지 않고 ID만 보관 — 향후 DB 분리를 고려한 설계 */
    @Column(name = "course_id", nullable = false)
    private Long courseId;

    /** 업로드한 판매자(users.id) */
    @Column(nullable = false)
    private Long ownerId;

    /** 워터마크에 삽입되는 추적용 식별자(UUID) */
    @Column(name = "asset_token", nullable = false, length = 36)
    private String assetToken;

    @Column(nullable = false, length = 255)
    private String originalFilename;

    @Column(nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false)
    private Integer width;

    @Column(nullable = false)
    private Integer height;

    /** 워터마크본 바이트 수 */
    @Column(nullable = false)
    private Long fileSize;

    /** 워터마크본의 SHA-256. 다운로드본과 동일성 비교에 사용 */
    @Column(nullable = false, length = 64)
    private String checksum;

    /** 이미지에 실제로 삽입된 페이로드 문자열 */
    @Column(nullable = false, length = 255)
    private String watermarkPayload;

    @Lob
    @Column(name = "watermarked_data", nullable = false, columnDefinition = "LONGBLOB")
    private byte[] watermarkedData;

    @Lob
    @Column(name = "preview_data", nullable = false, columnDefinition = "LONGBLOB")
    private byte[] previewData;

    @Column(nullable = false)
    @Builder.Default
    private Long downloadCount = 0L;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    public void increaseDownloadCount() {
        this.downloadCount++;
    }
}
