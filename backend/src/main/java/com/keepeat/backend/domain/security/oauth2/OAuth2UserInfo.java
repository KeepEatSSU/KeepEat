package com.keepeat.backend.domain.security.oauth2;

public interface OAuth2UserInfo {
    String getProviderId(); // 소셜 식별자 (구글의 sub)
    String getProvider();   // "google"
    String getEmail();
    String getName();
}
