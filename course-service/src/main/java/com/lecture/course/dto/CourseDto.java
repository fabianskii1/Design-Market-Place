package com.lecture.course.dto;

import com.lecture.course.entity.Course;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
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
         * 프론트가 img src 에 그대로 쓸 수 있는 경로.
         * DB에는 저장 파일명만 두고, 노출은 API 경로로 감싼다.
         * 저장 위치를 S3로 옮겨도 프론트 코드는 바뀌지 않는다.
         */
        private String thumbnailUrl;

        /** 자산 등록 여부. 프론트가 기본 이미지로 넘길지 판단한다. */
        private Boolean hasAsset;

        private Integer downloadCount;

        public static CourseResponse from(Course course) {
            boolean hasAsset = course.getOriginalUrl() != null
                    && !course.getOriginalUrl().isBlank();

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

    // 추천 서비스용 응답 (카테고리 기반 미구매 디자인 목록)
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RecommendResponse {
        private List<CourseResponse> courses;
        private Course.Category category;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LicenseTierResponse {
        private Long id;
        private Long courseId;
        private com.lecture.course.entity.LicenseTier.Tier tier;
        private BigDecimal price;
        private String description;

        public static LicenseTierResponse from(com.lecture.course.entity.LicenseTier entity) {
            return LicenseTierResponse.builder()
                    .id(entity.getId())
                    .courseId(entity.getCourseId())
                    .tier(entity.getTier())
                    .price(entity.getPrice())
                    .build();
        }
    }

    // 판매 대시보드: 강의 1건 판매 정보
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SalesItem {
        private Long courseId;
        private String title;
        private Long salesCount;
        private BigDecimal revenue;

        /** 이 강의의 라이선스 등급별 판매 집계. 프론트가 item.salesByLicense.find(l => l.tier === tier)로 매칭한다. */
        private List<LicenseSalesBreakdown> salesByLicense;
    }

    // 판매 대시보드: 강의 1건 안에서의 등급별 판매 집계 (salesByLicense 배열의 원소)
    // 필드명 고정: tier / count / revenue — 프론트 코드가 이 이름으로 직접 읽는다.
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LicenseSalesBreakdown {
        private com.lecture.course.entity.LicenseTier.Tier tier; // PERSONAL | COMMERCIAL_SMALL | COMMERCIAL_LARGE
        private Long count;
        private BigDecimal revenue;
    }

    // 판매 대시보드: 월별 매출 (monthlyBreakdown 배열의 원소)
    // 필드명 고정: month("YYYY-MM") / revenue — 프론트가 이 두 필드만 읽는다. salesCount는 안 써도 되지만 넣어둔다(무해).
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MonthlyBreakdownItem {
        private String month;
        private BigDecimal revenue;
        private Long salesCount;
    }

    // 판매 대시보드: 전체 응답
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SalesDashboardResponse {
        private List<SalesItem> items;
        private Long totalSalesCount;
        private BigDecimal totalRevenue;
        private List<MonthlyBreakdownItem> monthlyBreakdown;
        private Long subscriberCount;
    }

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LicenseTierRegisterRequest {
        @NotNull
        @Size(min = 1, message = "최소 1개 이상의 등급을 등록해야 합니다")
        @Valid
        private List<TierPrice> tiers;

        @Getter
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        public static class TierPrice {
            @NotNull(message = "등급은 필수입니다")
            private com.lecture.course.entity.LicenseTier.Tier tier;

            @NotNull(message = "가격은 필수입니다")
            @PositiveOrZero(message = "가격은 0 이상이어야 합니다")
            private BigDecimal price;
        }
    }
}
