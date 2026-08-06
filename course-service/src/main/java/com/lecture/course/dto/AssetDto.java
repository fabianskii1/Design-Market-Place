package com.lecture.course.dto;

import lombok.*;

import java.time.LocalDateTime;

public class AssetDto {

    /** 업로드 완료 후 반환되는 자산 메타데이터 */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AssetResponse {
        private Long assetId;
        private Long courseId;
        private Long ownerId;
        private String assetToken;
        private String originalFilename;
        private String contentType;
        private Integer width;
        private Integer height;
        private Long fileSize;
        private String checksum;
        private Long downloadCount;
        private LocalDateTime createdAt;
        private String previewUrl;
        private String downloadUrl;
    }

    /** 워터마크 추출·검증 결과 */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class VerifyResponse {
        /** 워터마크를 읽어냈는지 여부 */
        private boolean watermarkFound;
        /** 추출된 원문 페이로드 */
        private String payload;
        /** 페이로드가 우리 DB의 자산과 매칭되는지 */
        private boolean registered;
        private String assetToken;
        private Long courseId;
        private String courseTitle;
        private Long ownerId;
        private LocalDateTime issuedAt;
        /** 업로드된 파일이 우리가 배포한 원본과 바이트 단위로 동일한지 */
        private boolean checksumMatched;
        private String message;
    }
}
