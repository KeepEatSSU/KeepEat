package com.keepeat.backend.domain.security.oauth2;

import com.keepeat.backend.domain.user.entity.AppUser;
import com.keepeat.backend.domain.user.repository.AppUserRepository;
import com.keepeat.backend.domain.user.service.AppUserService;
import com.keepeat.backend.domain.security.JwtProvider;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtProvider jwtProvider;
    private final AppUserRepository appUserRepository;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");

        AppUser user = appUserRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));
        Long userId = user.getId();

        // 토큰 발급
        String accessToken = jwtProvider.createAccessToken(email, userId, user.getRole());
        String rawRefreshToken = jwtProvider.createRefreshToken(email, userId, user.getRole());

        // DB에는 해싱된 Refresh Token 저장 (일반 로그인 흐름과 동일)
        user.updateRefreshToken(AppUserService.hashToken(rawRefreshToken));
        appUserRepository.save(user);

        log.info("JWT 토큰 발급 및 DB 저장 완료 - Email: {}", email);

        // 프론트엔드로 리다이렉트
        String targetUrl = UriComponentsBuilder.fromUriString("http://localhost:3000/oauth2/redirect") // 요기 수정
                .queryParam("accessToken", accessToken)
                .queryParam("refreshToken", rawRefreshToken)
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}