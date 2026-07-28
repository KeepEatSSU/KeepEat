package com.keepeat.backend.domain.security.swagger;

import org.springframework.util.Assert;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class SwaggerLoginAttemptService {

    private static final int MAX_TRACKED_CLIENTS = 10_000;
    private static final long CLEANUP_INTERVAL_MASK = 255;
    private static final Duration CAPACITY_RETRY_AFTER = Duration.ofMinutes(1);

    private final ConcurrentHashMap<String, AttemptState> attempts = new ConcurrentHashMap<>();
    private final AtomicLong operations = new AtomicLong();
    private final int maxFailures;
    private final Duration observationWindow;
    private final Duration blockDuration;
    private final Clock clock;

    public SwaggerLoginAttemptService(
            int maxFailures,
            Duration observationWindow,
            Duration blockDuration
    ) {
        this(maxFailures, observationWindow, blockDuration, Clock.systemUTC());
    }

    SwaggerLoginAttemptService(
            int maxFailures,
            Duration observationWindow,
            Duration blockDuration,
            Clock clock
    ) {
        Assert.isTrue(maxFailures > 0, "maxFailures must be greater than zero");
        Assert.isTrue(observationWindow.isPositive(), "observationWindow must be positive");
        Assert.isTrue(blockDuration.isPositive(), "blockDuration must be positive");

        this.maxFailures = maxFailures;
        this.observationWindow = observationWindow;
        this.blockDuration = blockDuration;
        this.clock = clock;
    }

    public AttemptStatus currentStatus(String clientAddress) {
        String key = normalize(clientAddress);
        Instant now = clock.instant();
        cleanupPeriodically(now);

        AttemptState state = attempts.get(key);
        if (state == null) {
            return capacityExceeded() ? AttemptStatus.blocked(CAPACITY_RETRY_AFTER, false) : AttemptStatus.allowed();
        }

        if (state.isBlockedAt(now)) {
            return AttemptStatus.blocked(Duration.between(now, state.blockedUntil()), false);
        }

        if (state.isExpiredAt(now, observationWindow)) {
            attempts.remove(key, state);
        }
        return AttemptStatus.allowed();
    }

    public AttemptStatus recordFailure(String clientAddress) {
        String key = normalize(clientAddress);
        Instant now = clock.instant();
        cleanupPeriodically(now);

        if (!attempts.containsKey(key) && capacityExceeded()) {
            return AttemptStatus.blocked(CAPACITY_RETRY_AFTER, false);
        }

        AtomicReference<AttemptStatus> result = new AtomicReference<>();
        attempts.compute(key, (ignored, existing) -> {
            if (existing != null && existing.isBlockedAt(now)) {
                result.set(AttemptStatus.blocked(Duration.between(now, existing.blockedUntil()), false));
                return existing;
            }

            AttemptState active = existing;
            if (active == null || active.isExpiredAt(now, observationWindow)) {
                active = new AttemptState(now, 0, null);
            }

            int failures = active.failures() + 1;
            if (failures >= maxFailures) {
                Instant blockedUntil = now.plus(blockDuration);
                result.set(AttemptStatus.blocked(blockDuration, true));
                return new AttemptState(active.firstFailureAt(), failures, blockedUntil);
            }

            result.set(AttemptStatus.allowed());
            return new AttemptState(active.firstFailureAt(), failures, null);
        });
        return result.get();
    }

    public void recordSuccess(String clientAddress) {
        attempts.remove(normalize(clientAddress));
    }

    private void cleanupPeriodically(Instant now) {
        if ((operations.incrementAndGet() & CLEANUP_INTERVAL_MASK) != 0) {
            return;
        }
        attempts.entrySet().removeIf(entry -> entry.getValue().isExpiredAt(now, observationWindow));
    }

    private boolean capacityExceeded() {
        return attempts.size() >= MAX_TRACKED_CLIENTS;
    }

    private String normalize(String clientAddress) {
        return clientAddress == null || clientAddress.isBlank() ? "unknown" : clientAddress;
    }

    private record AttemptState(Instant firstFailureAt, int failures, Instant blockedUntil) {

        private boolean isBlockedAt(Instant now) {
            return blockedUntil != null && blockedUntil.isAfter(now);
        }

        private boolean isExpiredAt(Instant now, Duration observationWindow) {
            if (blockedUntil != null) {
                return !blockedUntil.isAfter(now);
            }
            return !firstFailureAt.plus(observationWindow).isAfter(now);
        }
    }

    public record AttemptStatus(boolean blocked, Duration retryAfter, boolean newlyBlocked) {

        private static AttemptStatus allowed() {
            return new AttemptStatus(false, Duration.ZERO, false);
        }

        private static AttemptStatus blocked(Duration retryAfter, boolean newlyBlocked) {
            return new AttemptStatus(true, retryAfter, newlyBlocked);
        }
    }
}
