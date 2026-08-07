package com.lecture.course.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 디자인 상품.
 * 테이블명은 기존 courses 를 그대로 재사용한다.
 */
@Entity
@Table(name = "courses")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Category category;

    /**
     * 대표가(최저 라이선스 가격) 캐시.
     * 목록 정렬·가격 필터 전용이며 결제 기준이 아니다.
     * 실제 결제 금액은 license_tiers.price 를 따른다.
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /** 디자이너 ID (users 는 타 서비스 소유이므로 ID만 보관) */
    @Column(name = "instructor_id", nullable = false)
    private Long instructorId;

    /** 구매자 수 (추천 서비스 정렬 기준) */
    @Column(name = "enrollment_count", nullable = false)
    @Builder.Default
    private Integer enrollmentCount = 0;

    // ── 디자인 자산 파일 ──────────────────────────────────────

    /**
     * 판매용 원본 경로.
     * 비가시적 워터마크만 삽입되어 있으며 구매자·소유자에게만 내려간다.
     */
    @Column(name = "original_url", length = 500)
    private String originalUrl;

    /**
     * 공개 미리보기 경로.
     * 가시적 + 비가시적 워터마크가 모두 들어가 있다.
     */
    @Column(name = "watermark_url", length = 500)
    private String watermarkUrl;

    /**
     * 목록·상세 카드가 쓰는 썸네일 경로.
     * 공개 이미지이므로 언제나 워터마크본을 가리킨다.
     */
    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    /** 원본본의 SHA-256. 검증 시 재가공 여부 판별에 쓴다. */
    @Column(name = "asset_checksum", length = 64)
    private String assetChecksum;

    /** 다운로드 수 노출용 */
    @Column(name = "download_count", nullable = false)
    @Builder.Default
    private Integer downloadCount = 0;

    // ────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.ACTIVE;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public enum Category {
        BACKEND, FRONTEND, DEVOPS, DATA_SCIENCE, MOBILE, SECURITY, DATABASE, OTHER
    }

    public enum Status {
        ACTIVE, INACTIVE
    }

    public void increaseEnrollmentCount() {
        this.enrollmentCount++;
    }

    public void increaseDownloadCount() {
        this.downloadCount++;
    }

    /**
     * 워터마크 처리 완료 후 자산 경로를 한 번에 반영한다.
     *
     * 원본(판매용)과 워터마크본(공개용)을 반드시 분리해서 넣어야 한다.
     * 같은 파일명을 넣으면 구매자도 워터마크가 박힌 이미지를 받게 된다.
     */
    public void updateAssets(String originalUrl, String watermarkUrl, String checksum) {
        this.originalUrl = originalUrl;
        this.watermarkUrl = watermarkUrl;
        this.thumbnailUrl = watermarkUrl;
        this.assetChecksum = checksum;
    }

    /** 자산이 등록되어 있는지 */
    public boolean hasAsset() {
        return originalUrl != null && !originalUrl.isBlank()
                && watermarkUrl != null && !watermarkUrl.isBlank();
    }

    /** 등급별 가격 등록·수정 후 대표가 캐시 갱신 */
    public void updateRepresentativePrice(BigDecimal lowestTierPrice) {
        this.price = lowestTierPrice;
    }

    public void updateInfo(String title, String description, Category category) {
        this.title = title;
        this.description = description;
        this.category = category;
    }

    public void deactivate() {
        this.status = Status.INACTIVE;
    }
}
