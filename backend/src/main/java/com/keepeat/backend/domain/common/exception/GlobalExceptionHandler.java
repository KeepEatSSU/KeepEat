package com.keepeat.backend.domain.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(KeepEatException.class)
    public ResponseEntity<Map<String, String>> handleKeepEatException(KeepEatException e) {
        ErrorCode code = e.getErrorCode();
        HttpStatus status = switch (code) {
            case USER_INGREDIENT_ACCESS_DENIED -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.NOT_FOUND;
        };

        return ResponseEntity.status(status).body(Map.of(
                "status", "error",
                "code", code.name(),
                "message", code.getMessage()
        ));
    }
}
