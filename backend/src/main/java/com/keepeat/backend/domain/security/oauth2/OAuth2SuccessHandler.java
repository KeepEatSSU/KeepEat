package com.keepeat.backend.domain.security.oauth2;

import com.keepeat.backend.domain.user.service.AppUserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final AppUserService appUserService;
    private final String oauth2RedirectUri;

    public OAuth2SuccessHandler(
            AppUserService appUserService,
            @Value("${app.oauth2.redirect-uri}") String oauth2RedirectUri
    ) {
        this.appUserService = appUserService;
        this.oauth2RedirectUri = oauth2RedirectUri;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();
        Object rawUserId = oauthUser.getAttribute("appUserId");
        if (!(rawUserId instanceof Number userId)) {
            throw new IllegalStateException("OAuth 사용자 식별자가 없습니다.");
        }

        String code = appUserService.issueOAuthLoginCode(userId.longValue());
        String targetUrl = UriComponentsBuilder.fromUriString(oauth2RedirectUri)
                .queryParam("code", code)
                .build()
                .toUriString();
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
