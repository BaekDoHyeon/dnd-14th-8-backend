package com.dnd.moyeolak.global.exception;

import com.dnd.moyeolak.global.response.ApiResponse;
import com.dnd.moyeolak.global.response.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE) // 다른 예외 처리기보다 우선순위 높게 설정
@RestControllerAdvice
public class GlobalExceptionAdvice {

    /** 비즈니스 예외 처리 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<?>> handleBusinessException(BusinessException ex) {
        log.warn("[{}] BusinessException 발생: {}", ex.getErrorCode(), ex.getMessage());
        if (ex.getData() != null) {
            return ResponseEntity
                    .status(ex.getErrorCode().getHttpStatus())
                    .body(ApiResponse.error(ex.getErrorCode(), ex.getData()));
        }
        return ResponseEntity
                .status(ex.getErrorCode().getHttpStatus())
                .body(ApiResponse.error(ex.getErrorCode()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage())
        );

        log.warn("Validation 실패: {}", errors);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.INVALID_PARAMETER, errors));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex) {

        Map<String, String> errors = Map.of(ex.getParameterName(), "필수 요청 파라미터입니다.");

        log.warn("필수 요청 파라미터 누락: {}", errors);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.MISSING_REQUIRED_FIELD, errors));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex) {
        log.warn("데이터 무결성 위반: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)  // 409
                .body(ApiResponse.error(ErrorCode.DUPLICATE_RESOURCE));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(NoResourceFoundException ex) {
        log.warn("존재하지 않는 경로 요청: {}", ex.getResourcePath());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ErrorCode.NOT_FOUND));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
        log.error("Unexpected exception 발생", ex);
        return ResponseEntity
                .status(ErrorCode.SERVER_ERROR.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.SERVER_ERROR));
    }
}
