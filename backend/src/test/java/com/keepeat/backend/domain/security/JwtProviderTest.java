package com.keepeat.backend.domain.security;

import com.keepeat.backend.domain.user.entity.Role;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtProviderTest {

    private final JwtProvider jwtProvider = new JwtProvider(
            "test-secret-key-test-secret-key-test-secret-key-test-secret-key",
            60_000,
            120_000
    );

    @Test
    void accessAndRefreshTokensHaveDifferentTypesAndSameSession() {
        String access = jwtProvider.createAccessToken("user@test.com", 1L, Role.ROLE_USER, "session-1");
        String refresh = jwtProvider.createRefreshToken("user@test.com", 1L, Role.ROLE_USER, "session-1");

        assertThat(jwtProvider.isAccessToken(access)).isTrue();
        assertThat(jwtProvider.isRefreshToken(access)).isFalse();
        assertThat(jwtProvider.isAccessToken(refresh)).isFalse();
        assertThat(jwtProvider.isRefreshToken(refresh)).isTrue();
        assertThat(jwtProvider.getSessionId(access)).isEqualTo("session-1");
        assertThat(jwtProvider.getSessionId(refresh)).isEqualTo("session-1");
    }
}
