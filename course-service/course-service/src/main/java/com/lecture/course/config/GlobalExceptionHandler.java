package com.lecture.course.config;

import com.lecture.course.dto.CourseDto;
import com.lecture.course.exception.AssetAccessDeniedException;
import com.lecture.course.exception.AssetNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;

/**
 * 상태 코드 규약은 프론트의 안내 문구와 1:1로 맞물려 있다.
 *
 *   400 → 서버 message 를 그대로 사용자에게 표시
 *   403 → "구매한 사용자만 내려받을 수 있습니다"
 *   404 → "API가 아직 준비되지 않았습니다"
 *   413 → "파일 크기가 허용 범위를 초과했습니다"
 *   500 → "서버 오류가 발생했습니다"
 *
 * 클라이언트 입력 오류를 500으로 흘리면 사용자가 무엇을 고쳐야 할지 알 수 없다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 자산·디자인 없음 → 404 */
    @ExceptionHandler(AssetNotFoundException.class)
    public ResponseEntity<CourseDto.ApiResponse<Void>> handleAssetNotFound(AssetNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(CourseDto.ApiResponse.error(e.getMessage()));
    }

    /** 다운로드 권한 없음 → 403 */
    @ExceptionHandler(AssetAccessDeniedException.class)
    public ResponseEntity<CourseDto.ApiResponse<Void>> handleAccessDenied(AssetAccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(CourseDto.ApiResponse.error(e.getMessage()));
    }

    /**
     * 업로드 용량 초과 → 413
     *
     * Spring 의 multipart 제한에 먼저 걸리면 서비스 코드까지 오지 않고
     * 여기서 바로 터진다. 그래서 별도로 잡아준다.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<CourseDto.ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(CourseDto.ApiResponse.error("파일 크기가 허용 범위를 초과했습니다. (최대 10MB)"));
    }

    /** X-User-Id 누락 → 400 */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<CourseDto.ApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException e) {
        return ResponseEntity.badRequest()
                .body(CourseDto.ApiResponse.error("인증 정보가 없습니다. 다시 로그인해주세요."));
    }

    /** 잘못된 입력 → 400 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<CourseDto.ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
                .body(CourseDto.ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CourseDto.ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(CourseDto.ApiResponse.error(message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CourseDto.ApiResponse<Void>> handleGeneral(Exception e) {
        // 예상 못 한 오류는 로그를 남겨야 원인을 추적할 수 있다
        log.error("[CourseService] 처리되지 않은 예외", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(CourseDto.ApiResponse.error("서버 오류가 발생했습니다"));
    }
}
