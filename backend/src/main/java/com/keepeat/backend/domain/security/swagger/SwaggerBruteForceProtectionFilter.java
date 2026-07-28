package com.keepeat.backend.domain.security.swagger;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class SwaggerBruteForceProtectionFilter extends OncePerRequestFilter {

    private static final String SWAGGER_ROLE = "ROLE_SWAGGER";
    private final SwaggerLoginAttemptService loginAttemptService;

    public SwaggerBruteForceProtectionFilter(SwaggerLoginAttemptService loginAttemptService) {
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!hasBasicCredentials(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientAddress = clientAddress(request);
        SwaggerLoginAttemptService.AttemptStatus status =
                loginAttemptService.currentStatus(clientAddress);
        if (status.blocked()) {
            SwaggerRateLimitResponse.write(response, status);
            return;
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (hasSwaggerRole(authentication)) {
                loginAttemptService.recordSuccess(clientAddress);
            }
        }
    }

    static boolean hasBasicCredentials(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        return authorization != null
                && authorization.length() >= 6
                && authorization.regionMatches(true, 0, "Basic ", 0, 6);
    }

    static String clientAddress(HttpServletRequest request) {
        String address = request.getRemoteAddr();
        return address == null || address.isBlank() ? "unknown" : address;
    }

    private boolean hasSwaggerRole(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> SWAGGER_ROLE.equals(authority.getAuthority()));
    }
}

final class SwaggerRateLimitResponse {

    private SwaggerRateLimitResponse() {
    }

    static void write(
            HttpServletResponse response,
            SwaggerLoginAttemptService.AttemptStatus status
    ) throws IOException {
        long retryAfterSeconds = Math.max(1, (status.retryAfter().toMillis() + 999) / 1_000);

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"code\":\"SWAGGER_AUTH_RATE_LIMITED\","
                        + "\"message\":\"Too many authentication attempts\"}"
        );
    }
}
