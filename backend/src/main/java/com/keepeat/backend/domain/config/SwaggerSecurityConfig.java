package com.keepeat.backend.domain.config;

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
import org.springframework.util.Assert;

@Configuration
@Profile("demo")
public class SwaggerSecurityConfig {

    private static final String[] SWAGGER_PATHS = {
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs",
            "/v3/api-docs/**"
    };

    @Bean
    @Order(1)
    public SecurityFilterChain swaggerFilterChain(
            HttpSecurity http,
            PasswordEncoder passwordEncoder,
            @Value("${app.security.swagger.username}") String username,
            @Value("${app.security.swagger.password}") String password
    ) throws Exception {
        Assert.hasText(username, "SWAGGER_USERNAME must not be blank");
        Assert.hasText(password, "SWAGGER_PASSWORD must not be blank");

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
                .httpBasic(basic ->
                        basic.realmName("KeepEat Swagger")
                );

        return http.build();
    }
}
