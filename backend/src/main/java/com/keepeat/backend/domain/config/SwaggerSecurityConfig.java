package com.keepeat.backend.domain.config;

import com.keepeat.backend.domain.security.swagger.SwaggerBasicAuthenticationEntryPoint;
import com.keepeat.backend.domain.security.swagger.SwaggerBruteForceProtectionFilter;
import com.keepeat.backend.domain.security.swagger.SwaggerLoginAttemptService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.util.Assert;

import java.time.Duration;

@Configuration
@Profile("demo")
public class SwaggerSecurityConfig {

    private static final String SWAGGER_REALM = "KeepEat Swagger";
    private static final String[] SWAGGER_PATHS = {
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs",
            "/v3/api-docs/**"
    };

    @Bean
    public SwaggerLoginAttemptService swaggerLoginAttemptService(
            @Value("${app.security.swagger.rate-limit.max-failures}") int maxFailures,
            @Value("${app.security.swagger.rate-limit.observation-window}") Duration observationWindow,
            @Value("${app.security.swagger.rate-limit.block-duration}") Duration blockDuration
    ) {
        return new SwaggerLoginAttemptService(maxFailures, observationWindow, blockDuration);
    }

    @Bean
    public SwaggerBasicAuthenticationEntryPoint swaggerBasicAuthenticationEntryPoint(
            SwaggerLoginAttemptService loginAttemptService
    ) {
        return new SwaggerBasicAuthenticationEntryPoint(SWAGGER_REALM, loginAttemptService);
    }

    @Bean
    @Order(1)
    public SecurityFilterChain swaggerFilterChain(
            HttpSecurity http,
            PasswordEncoder passwordEncoder,
            SwaggerLoginAttemptService loginAttemptService,
            SwaggerBasicAuthenticationEntryPoint authenticationEntryPoint,
            @Value("${app.security.swagger.username}") String username,
            @Value("${app.security.swagger.password}") String password
    ) throws Exception {
        Assert.hasText(username, "SWAGGER_USERNAME must not be blank");
        Assert.hasText(password, "SWAGGER_PASSWORD must not be blank");
        Assert.isTrue(
                password.length() >= 24,
                "SWAGGER_PASSWORD must be at least 24 characters"
        );

        UserDetails swaggerUser = User.withUsername(username)
                .password(passwordEncoder.encode(password))
                .roles("SWAGGER")
                .build();

        DaoAuthenticationProvider authenticationProvider =
                new DaoAuthenticationProvider(new InMemoryUserDetailsManager(swaggerUser));
        authenticationProvider.setPasswordEncoder(passwordEncoder);

        http
                .securityMatcher(SWAGGER_PATHS)
                .authenticationProvider(authenticationProvider)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth ->
                        auth.anyRequest().hasRole("SWAGGER")
                )
                .headers(headers -> headers
                        .addHeaderWriter(new StaticHeadersWriter(
                                "X-Robots-Tag",
                                "noindex, nofollow, noarchive"
                        ))
                        .addHeaderWriter(new StaticHeadersWriter(
                                "Referrer-Policy",
                                "no-referrer"
                        ))
                )
                .httpBasic(basic ->
                        basic.authenticationEntryPoint(authenticationEntryPoint)
                )
                .addFilterBefore(
                        new SwaggerBruteForceProtectionFilter(loginAttemptService),
                        BasicAuthenticationFilter.class
                );

        return http.build();
    }
}
