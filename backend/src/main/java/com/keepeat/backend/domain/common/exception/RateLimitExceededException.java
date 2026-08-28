package com.keepeat.backend.domain.common.exception;

import lombok.Getter;

// 거절 시점의 윈도우 잔여 시간을 Retry-After 헤더로 내려보내기 위해 함께 전달한다.
@Getter
public class RateLimitExceededException extends KeepEatException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super(ErrorCode.RATE_LIMIT_EXCEEDED);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
