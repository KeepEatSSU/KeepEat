package com.keepeat.backend.domain.config;

import com.keepeat.backend.domain.security.CustomAuthenticationEntryPoint;
import com.keepeat.backend.domain.security.JwtAuthenticationFilter;
import com.keepeat.backend.domain.security.JwtProvider;
import com.keepeat.backend.domain.security.oauth2.CustomOAuth2UserService;
import com.keepeat.backend.domain.security.oauth2.OAuth2SuccessHandler;
import com.keepeat.backend.domain.user.repository.AppUserRepository;
import com.keepeat.backend.domain.user.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtProvider jwtProvider;
    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final AppUserRepository appUserRepository;
    private final UserSessionRepository userSessionRepository;
    private final Environment environment;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.ignoringRequestMatchers(
                        "/api/**", "/oauth2/**", "/login/oauth2/**", "/actuator/**"
                ))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    // 알림 배치 측정용 수동 트리거는 perf 프로필에서만 연다 (#51). 다른 프로필에서는 컨트롤러도 없다
                    if (environment.acceptsProfiles(Profiles.of("perf"))) {
                        auth.requestMatchers("/api/internal/perf/**").permitAll();
                    }
                    auth
                        .requestMatchers("/api/users/signup","/api/users/login","/api/users/refresh","/api/users/oauth2/exchange","/error","/swagger-ui/**","/v3/api-docs/**","/actuator/health","/actuator/health/**","/actuator/prometheus").permitAll()
                        .requestMatchers("/admin/login").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/users/send").permitAll()
                        .requestMatchers("/api/users/verify").permitAll()
                        .requestMatchers(
                                "/api/users/password/find",
                                "/api/users/password/find/verify",
                                "/api/users/password/reset"
                        ).permitAll()
                        .anyRequest().authenticated();
                })
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(customAuthenticationEntryPoint)
                )

                .oauth2Login(oauth2 -> oauth2
                    .userInfoEndpoint(userInfo -> userInfo
                        .userService(customOAuth2UserService) // 구글 정보 받아오기
                    )
                    .successHandler(oAuth2SuccessHandler) // 받아온 정보로 JWT 만들어서 프론트로 던지기
                )

                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtProvider, appUserRepository, userSessionRepository),
                        UsernamePasswordAuthenticationFilter.class
                );
        return http.build();
    }
}
