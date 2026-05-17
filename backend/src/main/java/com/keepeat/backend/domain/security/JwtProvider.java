package com.keepeat.backend.domain.security;

import com.keepeat.backend.domain.user.entity.Role;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
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

        byte[] keyBytes = secretKey.getBytes();
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenValidityTime = accessTokenValidityTime;
        this.refreshTokenValidityTime = refreshTokenValidityTime;
    }

    public String createAccessToken(String email, Long id, Role role) {
        return createToken(email, id, role, accessTokenValidityTime);
    }

    public String createRefreshToken(String email, Long id, Role role) {
        return createToken(email, id, role, refreshTokenValidityTime);
    }

    private String createToken(String email, Long id, Role role, long validityTime) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + validityTime);

        return Jwts.builder()
                .subject(email)
                .claim("id", id)
                .claim("role", role.name())
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
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("id", Long.class); // claim에서 'id'라는 이름으로 넣은 Long 값을 꺼냄
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
}