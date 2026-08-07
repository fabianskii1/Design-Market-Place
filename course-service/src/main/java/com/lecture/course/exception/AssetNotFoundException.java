package com.lecture.course.exception;

/**
 * 자산 파일이 없을 때. 404로 매핑된다.
 *
 * 프론트는 404를 "아직 준비되지 않음"으로 안내하므로
 * 클라이언트 입력 오류(400)와 구분해서 던진다.
 */
public class AssetNotFoundException extends RuntimeException {
    public AssetNotFoundException(String message) {
        super(message);
    }
}
