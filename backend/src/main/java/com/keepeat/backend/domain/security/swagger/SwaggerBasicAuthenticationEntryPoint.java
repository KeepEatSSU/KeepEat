package com.keepeat.backend.domain.security.swagger;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;

import java.io.IOException;

public class SwaggerBasicAuthenticationEntryPoint extends BasicAuthenticationEntryPoint {

    private static final Logger log =
            LoggerFactory.getLogger(SwaggerBasicAuthenticationEntryPoint.class);

    private final SwaggerLoginAttemptService loginAttemptService;

    public SwaggerBasicAuthenticationEntryPoint(
            String realmName,
            SwaggerLoginAttemptService loginAttemptService
    ) {
        setRealmName(realmName);
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        if (SwaggerBruteForceProtectionFilter.hasBasicCredentials(request)) {
            String clientAddress = SwaggerBruteForceProtectionFilter.clientAddress(request);
            SwaggerLoginAttemptService.AttemptStatus status =
                    loginAttemptService.recordFailure(clientAddress);

            if (status.blocked()) {
                if (status.newlyBlocked()) {
                    log.warn(
                            "Swagger Basic authentication temporarily blocked for client {}",
                            clientAddress
                    );
                }
                SwaggerRateLimitResponse.write(response, status);
                return;
            }
        }

        super.commence(request, response, authException);
    }
}
