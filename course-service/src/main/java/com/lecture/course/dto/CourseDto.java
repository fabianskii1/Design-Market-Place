package com.lecture.course.dto;

import com.lecture.course.entity.Course;
import com.lecture.course.entity.LicenseTier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class CourseDto {

    // 디자인 등록 요청
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateRequest {

        @NotBlank(message = "디자인명은 필수입니다")
        private String title;

        private String description;

        @NotNull(message = "카테고리는 필수입니다")
        private Course.Category category;

        @NotNull(message = "가격은 필수입니다")
        @PositiveOrZero(message = "가격은 0 이상이어야 합니다")
        private BigDecimal price;
    }

    // 디자인 응답
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CourseResponse {
        private Long id;
        private String title;
        private String description;
        private Course.Category category;
        private BigDecimal price;
        private Long instructorId;
        private Integer enrollmentCount;
        private Course.Status status;
        private LocalDateTime createdAt;

        /**
         * 프론트가 img src 에 쓰는 경로. 언제나 워터마크본을 가리킨다.
         * DB에는 저장 파일명만 두고 노출은 API 경로로 감싸므로,
         * 저장 위치가 S3로 바뀌어도 프론트 코드는 그대로다.
         */
        private String thumbnailUrl;

        /** 자산 등록 여부. false면 프론트가 미리보기 요청을 보내지 않는다. */
        private Boolean hasAsset;

        private Integer downloadCount;

        public static CourseResponse from(Course course) {
            boolean hasAsset = course.hasAsset();

            return CourseResponse.builder()
                    .id(course.getId())
                    .title(course.getTitle())
                    .description(course.getDescription())
                    .category(course.getCategory())
                    .price(course.getPrice())
                    .instructorId(course.getInstructorId())
                    .enrollmentCount(course.getEnrollmentCount())
                    .status(course.getStatus())
                    .createdAt(course.getCreatedAt())
                    .thumbnailUrl(hasAsset
                            ? "/api/courses/" + course.getId() + "/asset/preview"
                            : null)
                    .hasAsset(hasAsset)
                    .downloadCount(course.getDownloadCount())
                    .build();
        }
    }

    // ── 라이선스 등급 ──────────────────────────────────────

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LicenseTierResponse {
        private Long id;
        private Long courseId;
        private LicenseTier.Tier tier;
        private BigDecimal price;
        private String description;

        public static LicenseTierResponse from(LicenseTier licenseTier) {
            return LicenseTierResponse.builder()
                    .id(licenseTier.getId())
                    .courseId(licenseTier.getCourseId())
                    .tier(licenseTier.getTier())
                    .price(licenseTier.getPrice())
                    //.description(licenseTier.getDescription())
                    .build();
        }
    }

    // ── 워터마크 검증 ──────────────────────────────────────

    /** 유출 사본의 출처 확인 결과 */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WatermarkVerifyResponse {

        /** 비가시적 워터마크가 추출됐는지 */
        private Boolean watermarkFound;

        /** 추출된 ID가 현재 DB에 존재하는 디자인인지 */
        private Boolean registered;

        private Long designId;
        private Long ownerId;
        private LocalDateTime issuedAt;

        /**
         * 원본 체크섬과 일치하는지.
         * false면 워터마크는 살아있으나 파일이 재가공된 것이다.
         */
        private Boolean checksumMatched;

        private String message;
    }

    // ── 판매 대시보드 ──────────────────────────────────────

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SalesItemResponse {
        private Long courseId;
        private String title;
        private Course.Category category;
        private BigDecimal price;
        private Integer salesCount;
        private BigDecimal revenue;
        private Integer downloadCount;
        private String thumbnailUrl;

        public static SalesItemResponse from(Course course, Integer salesCount, BigDecimal revenue) {
            return SalesItemResponse.builder()
                    .courseId(course.getId())
                    .title(course.getTitle())
                    .category(course.getCategory())
                    .price(course.getPrice() == null ? BigDecimal.ZERO : course.getPrice())
                    .salesCount(salesCount == null ? 0 : salesCount)
                    .revenue(revenue == null ? BigDecimal.ZERO : revenue)
                    .downloadCount(course.getDownloadCount())
                    .thumbnailUrl(course.hasAsset()
                            ? "/api/courses/" + course.getId() + "/asset/preview"
                            : null)
                    .build();
        }
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SalesDashboardResponse {
        private Long instructorId;
        private Integer designCount;
        private Integer totalSalesCount;
        private BigDecimal totalRevenue;
        private List<SalesItemResponse> items;
    }

    // 공통 API 응답 래퍼
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ApiResponse<T> {
        private boolean success;
        private String message;
        private T data;

        public static <T> ApiResponse<T> success(T data) {
            return ApiResponse.<T>builder()
                    .success(true)
                    .message("성공")
                    .data(data)
                    .build();
        }

        public static <T> ApiResponse<T> error(String message) {
            return ApiResponse.<T>builder()
                    .success(false)
                    .message(message)
                    .build();
        }
    }

    // 추천 서비스용 응답
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RecommendResponse {
        private List<CourseResponse> courses;
        private Course.Category category;
    }
}
