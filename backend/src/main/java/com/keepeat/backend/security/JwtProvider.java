package com.keepeat.backend.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtProvider {

    private final SecretKey key; // 최신 버전에서는 java.security.Key 대신 SecretKey를 권장합니다.
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

    public String createAccessToken(String email) {
        return createToken(email, accessTokenValidityTime);
    }

    public String createRefreshToken(String email) {
        return createToken(email, refreshTokenValidityTime);
    }

    private String createToken(String email, long validityTime) {
        Date now = new Date();
        Date validity = new Date(now.getTime() + validityTime);

        return Jwts.builder()
                .subject(email)
                .issuedAt(now)
                .expiration(validity)
                .signWith(key)
                .compact();
    }
}