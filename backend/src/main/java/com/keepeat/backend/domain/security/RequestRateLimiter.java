package com.keepeat.backend.domain.security;

import com.keepeat.backend.domain.common.exception.ErrorCode;
import com.keepeat.backend.domain.common.exception.KeepEatException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RequestRateLimiter {

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public void check(HttpServletRequest request, String action, String subject, int maxRequests, Duration window) {
        long now = System.currentTimeMillis();
        long windowMillis = window.toMillis();
        String address = clientAddress(request);
        increment(action + ':' + address + ":*", maxRequests * 10, now, windowMillis);
        increment(action + ':' + address + ':' + Integer.toHexString(subject == null ? 0 : subject.hashCode()),
                maxRequests, now, windowMillis);

        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(entry -> now >= entry.getValue().endsAt());
        }
    }

    private void increment(String key, int maxRequests, long now, long windowMillis) {
        Window current = windows.compute(key, (ignored, existing) -> {
            if (existing == null || now >= existing.endsAt()) {
                return new Window(now + windowMillis, 1);
            }
            return new Window(existing.endsAt(), existing.count() + 1);
        });

        if (current.count() > maxRequests) {
            throw new KeepEatException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }
    }

    private String clientAddress(HttpServletRequest request) {
        // Forwarded 헤더는 신뢰 프록시 설정을 거친 뒤에만 Spring이 remoteAddr에 반영하도록 한다.
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }

    private record Window(long endsAt, int count) {
    }
}
