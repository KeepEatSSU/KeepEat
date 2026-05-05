package com.keepeat.backend.domain.security.oauth2;

import java.util.Map;

public class GoogleUserInfo implements OAuth2UserInfo {
    private Map<String, Object> attributes; // 구글이 던져준 JSON 데이터

    public GoogleUserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    @Override
    public String getProviderId() { return (String) attributes.get("sub"); }

    @Override
    public String getProvider() { return "google"; }

    @Override
    public String getEmail() { return (String) attributes.get("email"); }

    @Override
    public String getName() { return (String) attributes.get("name"); }
}