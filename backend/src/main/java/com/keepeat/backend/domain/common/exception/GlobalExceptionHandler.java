package com.keepeat.backend.domain.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.dao.DataIntegrityViolationException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, String>> handleRateLimitExceededException(RateLimitExceededException e, HttpServletRequest request) {
        ErrorCode code = e.getErrorCode();
        log.warn("[{}] [{}] {}", code.name(), endpoint(request), code.getMessage());

        return ResponseEntity.status(code.getStatus())
                .header("Retry-After", String.valueOf(e.getRetryAfterSeconds()))
                .body(Map.of(
                        "status", "error",
                        "code", code.name(),
                        "message", code.getMessage()
                ));
    }

    @ExceptionHandler(KeepEatException.class)
    public ResponseEntity<Map<String, String>> handleKeepEatException(KeepEatException e, HttpServletRequest request) {
        ErrorCode code = e.getErrorCode();
        if (code.getStatus().is5xxServerError()) {
            log.error("[{}] [{}] {}", code.name(), endpoint(request), code.getMessage(), e);
        } else {
            log.warn("[{}] [{}] {}", code.name(), endpoint(request), code.getMessage());
        }

        ResponseEntity.BodyBuilder response = ResponseEntity.status(code.getStatus());

        if (code == ErrorCode.RATE_LIMIT_EXCEEDED) {
            response.header("Retry-After", "60");
        }

        return response.body(Map.of(
                "status", "error",
                "code", code.name(),
                "message", code.getMessage()
        ));
    }
  
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException e, HttpServletRequest request) {
        log.warn("[{}] [{}] {}", "BAD_REQUEST", endpoint(request) ,e.getMessage());

        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("status", "error");
        errorResponse.put("code", "BAD_REQUEST");
        errorResponse.put("message", e.getMessage());
        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleMethodArgumentNotValidException(MethodArgumentNotValidException e, HttpServletRequest request){
        log.warn("[{}] [{}] {}", "INVALID_INPUT", endpoint(request), e.getMessage());
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("status", "error");
        errorResponse.put("code", "INVALID_INPUT");
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null ? "입력값 형식이 올바르지 않습니다." : error.getDefaultMessage())
                .orElse("입력값 형식이 올바르지 않습니다.");
        errorResponse.put("message", message);
        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            ConstraintViolationException.class
    })
    public ResponseEntity<Map<String, String>> handleMalformedRequest(Exception e) {
        return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "code", "INVALID_INPUT",
                "message", "입력값 형식이 올바르지 않습니다."
        ));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        log.warn("Data integrity violation: {}", e.getMostSpecificCause().getClass().getSimpleName());
        return ResponseEntity.status(409).body(Map.of(
                "status", "error",
                "code", "DATA_CONFLICT",
                "message", "이미 처리되었거나 충돌하는 요청입니다."
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleException(Exception e, HttpServletRequest request){
        // 클라이언트가 자기 잘못으로 오해하지 않도록 500으로 응답하고,
        // 운영 디버깅을 위해 서버 로그에 전체 stacktrace를 남긴다.
        log.error("[{}] [{}] {}", "INTERNAL_ERROR", endpoint(request), e.getMessage(), e);
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("status", "error");
        errorResponse.put("code", "INTERNAL_ERROR");
        errorResponse.put("message", "서버 내부 오류가 발생했습니다");

        return ResponseEntity.internalServerError().body(errorResponse);

    }

    private String endpoint(HttpServletRequest request) {
        return request.getMethod() + " " + request.getRequestURI();
    }

}
