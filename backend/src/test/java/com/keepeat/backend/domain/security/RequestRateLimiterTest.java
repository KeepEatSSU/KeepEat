package com.keepeat.backend.domain.security;

import com.keepeat.backend.domain.common.exception.ErrorCode;
import com.keepeat.backend.domain.common.exception.KeepEatException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestRateLimiterTest {

    @Test
    void rejectsRequestsOverTheLimit() {
        RequestRateLimiter limiter = new RequestRateLimiter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.1");

        limiter.check(request, "login", "user@test.com", 2, Duration.ofMinutes(1));
        limiter.check(request, "login", "user@test.com", 2, Duration.ofMinutes(1));

        assertThatThrownBy(() -> limiter.check(request, "login", "user@test.com", 2, Duration.ofMinutes(1)))
                .isInstanceOfSatisfying(KeepEatException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED));
    }
}
