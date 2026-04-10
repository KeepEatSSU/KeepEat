package com.keepeat.backend.domain.common.exception;

import lombok.Getter;

@Getter
public class KeepEatException extends RuntimeException {

    private final ErrorCode errorCode;

    public KeepEatException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
