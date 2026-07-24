package com.keepeat.backend.domain.security;

import com.keepeat.backend.domain.user.entity.Role;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtProvider {

    private final SecretKey key;
    private final long accessTokenValidityTime;
    private final long refreshTokenValidityTime;

    public JwtProvider(
            @Value("${jwt.secret}") String secretKey,
            @Value("${jwt.access-expiration}") long accessTokenValidityTime,
            @Value("${jwt.refresh-expiration}") long refreshTokenValidityTime) {

        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenValidityTime = accessTokenValidityTime;
        this.refreshTokenValidityTime = refreshTokenValidityTime;
    }

    public String createAccessToken(String email, Long id, Role role, String sessionId) {
        return createToken(email, id, role, sessionId, "access", accessTokenValidityTime);
    }

    public String createRefreshToken(String email, Long id, Role role, String sessionId) {
        return createToken(email, id, role, sessionId, "refresh", refreshTokenValidityTime);
    }

    private String createToken(String email, Long id, Role role, String sessionId, String tokenType, long validityTime) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + validityTime);

        return Jwts.builder()
                .subject(email)
                .claim("id", id)
                .claim("role", role.name())
                .claim("tokenType", tokenType)
                .claim("sid", sessionId)
                .issuedAt(now)
                .expiration(validity)
                .signWith(key)
                .compact();
    }

    // 토큰 유효성 검사
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // 토큰에서 유저 이메일 꺼내기
    public String getEmail(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public Long getId(String token) {
        Number id = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("id", Number.class);
        return id.longValue();
    }

    // role claim이 없는 구버전 토큰은 ROLE_USER로 폴백 — 기존 발급 토큰 호환
    public String getRole(String token) {
        String role = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("role", String.class);
        return role != null ? role : Role.ROLE_USER.name();
    }

    public String getSessionId(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("sid", String.class);
    }

    public boolean isAccessToken(String token) {
        String type = getTokenType(token);
        return "access".equals(type);
    }

    public boolean isRefreshToken(String token) {
        String type = getTokenType(token);
        return type == null || "refresh".equals(type);
    }

    public long getRefreshTokenValidityTime() {
        return refreshTokenValidityTime;
    }

    private String getTokenType(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("tokenType", String.class);
    }
}
