package com.keepeat.backend.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "oauth_login_code")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OAuthLoginCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 128)
    private String codeHash;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime consumedAt;

    private OAuthLoginCode(String codeHash, Long userId, LocalDateTime expiresAt) {
        this.codeHash = codeHash;
        this.userId = userId;
        this.expiresAt = expiresAt;
        this.createdAt = LocalDateTime.now();
    }

    public static OAuthLoginCode issue(String codeHash, Long userId, LocalDateTime expiresAt) {
        return new OAuthLoginCode(codeHash, userId, expiresAt);
    }

    public boolean isUsable(LocalDateTime now) {
        return consumedAt == null && !now.isAfter(expiresAt);
    }

    public void consume(LocalDateTime now) {
        this.consumedAt = now;
    }
}
