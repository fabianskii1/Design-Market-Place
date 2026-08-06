package com.lecture.course.dto;

import com.lecture.course.entity.Course;
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
}