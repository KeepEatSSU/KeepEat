package com.keepeat.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    // 1. 비밀번호를 암호화해주는 도구를 스프링 빈(Bean)으로 등록
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 2. HTTP 보안 설정 (누가 어떤 주소에 접근할 수 있는지 설정)
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                // JWT를 쓰려면 세션을 절대 사용하지 않겠다고 선언해야 합니다!
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 진짜 에러 메시지를 볼 수 있도록 "/error" 주소를 허용해 줍니다.
                        .requestMatchers("/api/users/signup", "/api/users/login", "/error").permitAll()
                        .anyRequest().authenticated()
                );
        return http.build();
    }
}