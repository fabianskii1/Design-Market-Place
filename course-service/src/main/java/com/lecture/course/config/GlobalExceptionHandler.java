package com.lecture.course.config;

import com.lecture.course.dto.CourseDto;
import com.lecture.course.service.AssetService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.stream.Collectors;

/**
 * 전역 예외 처리.
 *
 * 원칙 두 가지:
 *  1) 클라이언트가 고칠 수 있는 문제는 4xx로, 서버 잘못은 5xx로 구분한다.
 *  2) 어떤 예외든 반드시 로그를 남긴다. 로그 없는 예외 처리는 장애 원인을 영구히 잃는 것과 같다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<CourseDto.ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("잘못된 요청: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(CourseDto.ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(AssetService.AccessDeniedByOwnerException.class)
    public ResponseEntity<CourseDto.ApiResponse<Void>> handleAccessDenied(
            AssetService.AccessDeniedByOwnerException e) {
        log.warn("권한 없음: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(CourseDto.ApiResponse.error(e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CourseDto.ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("검증 실패: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(CourseDto.ApiResponse.error(message));
    }

    /**
     * JSON 역직렬화 실패. 정의되지 않은 Enum 값을 보낸 경우가 대표적이다.
     * 이전에는 이 예외가 아래 handleGeneral로 흘러가 500으로 반환되면서,
     * 클라이언트가 무엇을 잘못했는지 알 수 없었다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<CourseDto.ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("요청 본문을 해석할 수 없음: {}", e.getMostSpecificCause().getMessage());
        return ResponseEntity.badRequest()
                .body(CourseDto.ApiResponse.error(
                        "요청 형식이 올바르지 않습니다. 허용되지 않은 값이 포함되어 있는지 확인해 주세요."));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<CourseDto.ApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException e) {
        log.warn("필수 헤더 누락: {}", e.getHeaderName());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(CourseDto.ApiResponse.error("인증 정보가 없습니다. 로그인 후 다시 시도해 주세요."));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<CourseDto.ApiResponse<Void>> handleUploadSize(MaxUploadSizeExceededException e) {
        log.warn("업로드 용량 초과: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(CourseDto.ApiResponse.error("파일 크기가 허용 범위를 초과했습니다. (최대 10MB)"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CourseDto.ApiResponse<Void>> handleGeneral(Exception e) {
        log.error("처리되지 않은 예외 발생", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(CourseDto.ApiResponse.error("서버 오류가 발생했습니다"));
    }
}
