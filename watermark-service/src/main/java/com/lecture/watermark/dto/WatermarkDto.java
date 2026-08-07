package com.lecture.watermark.dto;

import lombok.*;

import java.time.LocalDateTime;

public class WatermarkDto {

    /** 유출본 추적 결과 */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TraceResponse {
        private Boolean watermarkFound;
        private String payload;

        private Long courseId;
        private Long ownerId;

        /** 구매자 사본에만 존재한다. null 이면 유출 경로를 특정할 수 없다. */
        private Long buyerId;

        private LocalDateTime issuedAt;

        /** 구매 이력에서 일치하는 건을 찾았는지 */
        private Boolean traced;

        /** 배포한 사본과 바이트 단위로 동일한지 */
        private Boolean checksumMatched;

        private String message;
    }
}
