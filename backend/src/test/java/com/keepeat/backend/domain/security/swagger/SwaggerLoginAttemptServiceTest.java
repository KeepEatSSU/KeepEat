package com.keepeat.backend.domain.security.swagger;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwaggerLoginAttemptServiceTest {

    private static final String CLIENT_ADDRESS = "192.0.2.100";

    private final MutableClock clock =
            new MutableClock(Instant.parse("2026-07-28T00:00:00Z"));
    private final SwaggerLoginAttemptService service =
            new SwaggerLoginAttemptService(
                    3,
                    Duration.ofMinutes(10),
                    Duration.ofMinutes(15),
                    clock
            );

    @Test
    void clientIsBlockedAtThresholdAndReleasedAfterBlockDuration() {
        assertFalse(service.recordFailure(CLIENT_ADDRESS).blocked());
        assertFalse(service.recordFailure(CLIENT_ADDRESS).blocked());

        SwaggerLoginAttemptService.AttemptStatus blocked =
                service.recordFailure(CLIENT_ADDRESS);

        assertTrue(blocked.blocked());
        assertTrue(blocked.newlyBlocked());
        assertTrue(service.currentStatus(CLIENT_ADDRESS).blocked());

        clock.advance(Duration.ofMinutes(15));

        assertFalse(service.currentStatus(CLIENT_ADDRESS).blocked());
    }

    @Test
    void successfulAuthenticationClearsFailureHistory() {
        assertFalse(service.recordFailure(CLIENT_ADDRESS).blocked());
        assertFalse(service.recordFailure(CLIENT_ADDRESS).blocked());

        service.recordSuccess(CLIENT_ADDRESS);

        assertFalse(service.recordFailure(CLIENT_ADDRESS).blocked());
        assertFalse(service.recordFailure(CLIENT_ADDRESS).blocked());
        assertTrue(service.recordFailure(CLIENT_ADDRESS).blocked());
    }

    @Test
    void failuresOutsideObservationWindowStartANewWindow() {
        assertFalse(service.recordFailure(CLIENT_ADDRESS).blocked());
        assertFalse(service.recordFailure(CLIENT_ADDRESS).blocked());

        clock.advance(Duration.ofMinutes(10));

        assertFalse(service.recordFailure(CLIENT_ADDRESS).blocked());
        assertFalse(service.recordFailure(CLIENT_ADDRESS).blocked());
        assertTrue(service.recordFailure(CLIENT_ADDRESS).blocked());
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
