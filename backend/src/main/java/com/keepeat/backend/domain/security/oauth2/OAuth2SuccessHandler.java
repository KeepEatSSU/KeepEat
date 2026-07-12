package com.keepeat.backend.domain.security.oauth2;

import com.keepeat.backend.domain.user.entity.AppUser;
import com.keepeat.backend.domain.user.entity.OAuthLoginCode;
import com.keepeat.backend.domain.user.repository.AppUserRepository;
import com.keepeat.backend.domain.user.repository.OAuthLoginCodeRepository;
import com.keepeat.backend.domain.user.service.AppUserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Slf4j
@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final int OAUTH_LOGIN_CODE_EXPIRATION_MINUTES = 3;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AppUserRepository appUserRepository;
    private final OAuthLoginCodeRepository oauthLoginCodeRepository;
    private final String oauth2RedirectUri;

    public OAuth2SuccessHandler(
            AppUserRepository appUserRepository,
            OAuthLoginCodeRepository oauthLoginCodeRepository,
            @Value("${app.oauth2.redirect-uri}") String oauth2RedirectUri
    ) {
        this.appUserRepository = appUserRepository;
        this.oauthLoginCodeRepository = oauthLoginCodeRepository;
        this.oauth2RedirectUri = oauth2RedirectUri;
    }

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");

        AppUser user = appUserRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));

        LocalDateTime now = LocalDateTime.now();
        oauthLoginCodeRepository.deleteAllByExpiresAtBefore(now);

        String code = generateOpaqueCode();
        oauthLoginCodeRepository.save(OAuthLoginCode.issue(
                AppUserService.hashToken(code),
                user.getId(),
                now.plusMinutes(OAUTH_LOGIN_CODE_EXPIRATION_MINUTES)
        ));

        log.info("OAuth 일회용 로그인 코드 발급 완료 - Email: {}", email);

        String targetUrl = UriComponentsBuilder.fromUriString(oauth2RedirectUri)
                .queryParam("code", code)
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    private String generateOpaqueCode() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
