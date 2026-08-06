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

    /** 원본 파일 스토리지 경로 */
    @Column(name = "original_url", length = 500)
    private String originalUrl;

    /** 워터마크 적용본 경로 */
    @Column(name = "watermark_url", length = 500)
    private String watermarkUrl;

    /** 미리보기용 썸네일 경로 */
    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

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

    /** 업로드 완료 후 원본·썸네일 경로 반영 */
    public void updateAssets(String originalUrl, String thumbnailUrl) {
        this.originalUrl = originalUrl;
        this.thumbnailUrl = thumbnailUrl;
    }

    /** 워터마크 처리 완료 후 반영 */
    public void updateWatermarkUrl(String watermarkUrl) {
        this.watermarkUrl = watermarkUrl;
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