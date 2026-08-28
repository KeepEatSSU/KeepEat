package com.keepeat.backend.domain.security;

import com.keepeat.backend.domain.common.exception.ErrorCode;
import com.keepeat.backend.domain.common.exception.KeepEatException;
import com.keepeat.backend.domain.common.exception.RateLimitExceededException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestRateLimiterTest {

    // 고정 윈도우의 경계를 결정적으로 검증하기 위해 시계를 제어한다
    private static class FixedClockRateLimiter extends RequestRateLimiter {
        private long now = 1_000L;

        @Override
        long currentTimeMillis() {
            return now;
        }

        void advanceMillis(long millis) {
            now += millis;
        }
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.1");
        return request;
    }

    @Test
    void rejectsRequestsOverTheLimit() {
        RequestRateLimiter limiter = new RequestRateLimiter();
        MockHttpServletRequest request = request();

        limiter.check(request, "login", "user@test.com", 2, Duration.ofMinutes(1));
        limiter.check(request, "login", "user@test.com", 2, Duration.ofMinutes(1));

        assertThatThrownBy(() -> limiter.check(request, "login", "user@test.com", 2, Duration.ofMinutes(1)))
                .isInstanceOfSatisfying(KeepEatException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED));
    }

    @Test
    void retryAfterEqualsFullWindowRightAfterWindowStart() {
        FixedClockRateLimiter limiter = new FixedClockRateLimiter();
        MockHttpServletRequest request = request();

        limiter.check(request, "login", "user@test.com", 2, Duration.ofMinutes(1));
        limiter.check(request, "login", "user@test.com", 2, Duration.ofMinutes(1));

        // 윈도우 시작 직후의 거절: 잔여 시간 = 윈도우 전체(60초)
        assertThatThrownBy(() -> limiter.check(request, "login", "user@test.com", 2, Duration.ofMinutes(1)))
                .isInstanceOfSatisfying(RateLimitExceededException.class, exception ->
                        assertThat(exception.getRetryAfterSeconds()).isEqualTo(60L));
    }

    @Test
    void retryAfterShrinksNearWindowExpiry() {
        FixedClockRateLimiter limiter = new FixedClockRateLimiter();
        MockHttpServletRequest request = request();

        limiter.check(request, "login", "user@test.com", 2, Duration.ofMinutes(1));
        limiter.check(request, "login", "user@test.com", 2, Duration.ofMinutes(1));

        // 만료 500ms 전의 거절: 잔여 시간 = 올림하여 1초
        limiter.advanceMillis(59_500L);
        assertThatThrownBy(() -> limiter.check(request, "login", "user@test.com", 2, Duration.ofMinutes(1)))
                .isInstanceOfSatisfying(RateLimitExceededException.class, exception ->
                        assertThat(exception.getRetryAfterSeconds()).isEqualTo(1L));
    }

    @Test
    void retryAfterRoundsUpMidWindow() {
        FixedClockRateLimiter limiter = new FixedClockRateLimiter();
        MockHttpServletRequest request = request();

        limiter.check(request, "login", "user@test.com", 2, Duration.ofMinutes(1));
        limiter.check(request, "login", "user@test.com", 2, Duration.ofMinutes(1));

        // 잔여 30.5초는 내림(30)이 아니라 올림(31)으로 보고돼야 한다
        limiter.advanceMillis(29_500L);
        assertThatThrownBy(() -> limiter.check(request, "login", "user@test.com", 2, Duration.ofMinutes(1)))
                .isInstanceOfSatisfying(RateLimitExceededException.class, exception ->
                        assertThat(exception.getRetryAfterSeconds()).isEqualTo(31L));
    }

    @Test
    void coarsePerIpLimitReportsItsOwnWindowRemainingTime() {
        FixedClockRateLimiter limiter = new FixedClockRateLimiter();
        MockHttpServletRequest request = request();

        // 서로 다른 subject 20개로 IP 전체 한도(maxRequests * 10 = 20)만 채운다
        for (int i = 0; i < 20; i++) {
            limiter.check(request, "login", "user" + i + "@test.com", 2, Duration.ofMinutes(1));
        }

        // 30초 뒤 새 subject의 요청: fine 윈도우는 새것이지만 coarse 윈도우의 잔여 30초가 보고돼야 한다
        limiter.advanceMillis(30_000L);
        assertThatThrownBy(() -> limiter.check(request, "login", "someone-new@test.com", 2, Duration.ofMinutes(1)))
                .isInstanceOfSatisfying(RateLimitExceededException.class, exception ->
                        assertThat(exception.getRetryAfterSeconds()).isEqualTo(30L));
    }

    @Test
    void retryAfterIsAtLeastOneSecondAtTheVeryEdgeOfTheWindow() {
        FixedClockRateLimiter limiter = new FixedClockRateLimiter();
        MockHttpServletRequest request = request();

        limiter.check(request, "login", "user@test.com", 2, Duration.ofMinutes(1));
        limiter.check(request, "login", "user@test.com", 2, Duration.ofMinutes(1));

        // 만료 1ms 전이라도 Retry-After는 최소 1초
        limiter.advanceMillis(59_999L);
        assertThatThrownBy(() -> limiter.check(request, "login", "user@test.com", 2, Duration.ofMinutes(1)))
                .isInstanceOfSatisfying(RateLimitExceededException.class, exception ->
                        assertThat(exception.getRetryAfterSeconds()).isEqualTo(1L));
    }

    @Test
    void allowsRequestsAgainAfterWindowExpiry() {
        FixedClockRateLimiter limiter = new FixedClockRateLimiter();
        MockHttpServletRequest request = request();

        limiter.check(request, "login", "user@test.com", 2, Duration.ofMinutes(1));
        limiter.check(request, "login", "user@test.com", 2, Duration.ofMinutes(1));

        limiter.advanceMillis(60_000L);
        assertThatCode(() -> limiter.check(request, "login", "user@test.com", 2, Duration.ofMinutes(1)))
                .doesNotThrowAnyException();
    }
}
