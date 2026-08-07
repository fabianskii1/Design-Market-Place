package com.lecture.course.exception;

/**
 * 원본 다운로드 권한이 없을 때. 403으로 매핑된다.
 * 프론트는 "구매한 사용자만 내려받을 수 있습니다"로 안내한다.
 */
public class AssetAccessDeniedException extends RuntimeException {
    public AssetAccessDeniedException(String message) {
        super(message);
    }
}
