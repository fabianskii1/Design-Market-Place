package com.lecture.enrollment.dto;

import com.lecture.enrollment.entity.Enrollment;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class EnrollmentDto {

    // 수강신청 요청
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EnrollRequest {
    @NotNull(message = "강의 ID는 필수입니다")
    private Long courseId;

    @NotNull(message = "라이선스 등급 ID는 필수입니다")
    private Long licenseTier;   // JSON 필드명 = licenseTier (프론트 계약에 맞춤), 값은 license_tiers.id
    }

    // 강의 요약 정보 (내 수강 목록 표시용)
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CourseSummary {
        private Long id;
        private String title;
        private String description;
        private String category;
        private Integer price;
        private String thumbnail;
        private String instructorName;
        private Integer enrollmentCount;
    }

    // 수강 응답
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EnrollmentResponse {
        private Long id;
        private Long userId;
        private Long courseId;
        private Enrollment.Status status;
        private LocalDateTime createdAt;
        private Long licenseTierId;
        private BigDecimal paidAmount;

        // 추가
        private CourseSummary course;

        public static EnrollmentResponse from(Enrollment enrollment) {
            return EnrollmentResponse.builder()
                    .id(enrollment.getId())
                    .userId(enrollment.getUserId())
                    .courseId(enrollment.getCourseId())
                    .status(enrollment.getStatus())
                    .createdAt(enrollment.getCreatedAt())
                    .licenseTierId(enrollment.getLicenseTierId())
                    .paidAmount(enrollment.getPaidAmount())
                    .build();
        }

        public static EnrollmentResponse from(Enrollment enrollment, CourseSummary course) {
            return EnrollmentResponse.builder()
                    .id(enrollment.getId())
                    .userId(enrollment.getUserId())
                    .courseId(enrollment.getCourseId())
                    .status(enrollment.getStatus())
                    .createdAt(enrollment.getCreatedAt())
                    .licenseTierId(enrollment.getLicenseTierId())
                    .paidAmount(enrollment.getPaidAmount())
                    .course(course)
                    .build();
        }
    }

    // 추천 서비스용: 수강 이력 조회 응답
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EnrollmentHistoryResponse {
        private Long userId;
        private List<Long> activeCourseIds;
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

    
}