package com.keepeat.backend.domain.security.oauth2;

import com.keepeat.backend.domain.user.entity.AppUser;
import com.keepeat.backend.domain.user.entity.Role;
import com.keepeat.backend.domain.user.repository.AppUserRepository;
import com.keepeat.backend.domain.user.service.EmailNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private static final String GOOGLE = "google";

    private final AppUserRepository appUserRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauthUser = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        if (!GOOGLE.equals(registrationId)) {
            throw oauthError("unsupported_provider", "지원하지 않는 OAuth 제공자입니다.");
        }

        GoogleUserInfo info = new GoogleUserInfo(oauthUser.getAttributes());
        if (!isVerifiedEmail(oauthUser.getAttributes())) {
            throw oauthError("unverified_email", "검증되지 않은 이메일로는 로그인할 수 없습니다.");
        }

        String provider = info.getProvider();
        String providerId = info.getProviderId();
        String email = EmailNormalizer.normalize(info.getEmail());
        if (providerId == null || email == null) {
            throw oauthError("invalid_user_info", "OAuth 사용자 정보가 올바르지 않습니다.");
        }

        AppUser user = appUserRepository.findByProviderAndProviderId(provider, providerId)
                .orElseGet(() -> createOrMigrateOAuthUser(info, email));

        Map<String, Object> attributes = new HashMap<>(oauthUser.getAttributes());
        attributes.put("appUserId", user.getId());
        return new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority(user.getRole().name())),
                attributes,
                "appUserId"
        );
    }

    private AppUser createOrMigrateOAuthUser(GoogleUserInfo info, String email) {
        return appUserRepository.findByEmailIgnoreCase(email)
                .map(existing -> {
                    if (!info.getProvider().equals(existing.getProvider()) || existing.getProviderId() != null) {
                        throw oauthError("account_conflict", "동일 이메일로 가입된 다른 로그인 방식의 계정이 있습니다.");
                    }
                    existing.attachOAuthIdentity(info.getProvider(), info.getProviderId());
                    return existing;
                })
                .orElseGet(() -> appUserRepository.save(new AppUser(
                        safeNickname(info.getName()),
                        email,
                        info.getProvider(),
                        info.getProviderId(),
                        Role.ROLE_USER
                )));
    }

    private boolean isVerifiedEmail(Map<String, Object> attributes) {
        Object verified = attributes.get("email_verified");
        return Boolean.TRUE.equals(verified) || "true".equalsIgnoreCase(String.valueOf(verified));
    }

    private String safeNickname(String name) {
        if (name == null || name.isBlank()) {
            return "KeepEat 사용자";
        }
        String trimmed = name.trim();
        return trimmed.length() <= 50 ? trimmed : trimmed.substring(0, 50);
    }

    private OAuth2AuthenticationException oauthError(String code, String message) {
        return new OAuth2AuthenticationException(new OAuth2Error(code), message);
    }
}
