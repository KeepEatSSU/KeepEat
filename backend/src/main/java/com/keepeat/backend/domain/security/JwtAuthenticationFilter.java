package com.keepeat.backend.domain.security;

import com.keepeat.backend.domain.user.entity.AppUser;
import com.keepeat.backend.domain.user.repository.AppUserRepository;
import com.keepeat.backend.domain.user.repository.UserSessionRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String ADMIN_TOKEN_COOKIE = "ADMIN_TOKEN";

    private final JwtProvider jwtProvider;
    private final AppUserRepository appUserRepository;
    private final UserSessionRepository userSessionRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {


        String token = resolveToken(request);
        if (token != null && jwtProvider.validateToken(token) && jwtProvider.isAccessToken(token)) {

            Long id = jwtProvider.getId(token);
            AppUser user = appUserRepository.findById(id).orElse(null);
            if (user == null) {
                filterChain.doFilter(request, response);
                return;
            }

            String sessionId = jwtProvider.getSessionId(token);
            if (sessionId == null || userSessionRepository.findByIdAndUserId(sessionId, id)
                    .filter(session -> session.isUsable(Instant.now()))
                    .isEmpty()) {
                filterChain.doFilter(request, response);
                return;
            }

            String role = user.getRole().name();

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(id, null, List.of(new SimpleGrantedAuthority(role)));
            authentication.setDetails(sessionId);

            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7); // "Bearer " 이후의 텍스트만 가져옴
        }

        // 어드민 페이지는 HttpOnly 쿠키로 토큰을 전달 — 헤더가 없으면 쿠키도 본다
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (ADMIN_TOKEN_COOKIE.equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
