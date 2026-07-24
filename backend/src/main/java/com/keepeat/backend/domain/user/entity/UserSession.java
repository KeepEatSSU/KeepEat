package com.keepeat.backend.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_session", indexes = {
        @Index(name = "idx_user_session_user_id", columnList = "user_id"),
        @Index(name = "idx_user_session_expires_at", columnList = "expires_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSession {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true, length = 128)
    private String refreshTokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant lastRotatedAt;

    private UserSession(String id, Long userId, String refreshTokenHash, Instant expiresAt) {
        this.id = id;
        this.userId = userId;
        this.refreshTokenHash = refreshTokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
        this.lastRotatedAt = this.createdAt;
    }

    public static UserSession create(Long userId, String refreshTokenHash, Instant expiresAt) {
        return new UserSession(UUID.randomUUID().toString(), userId, refreshTokenHash, expiresAt);
    }

    public static UserSession create(String id, Long userId, String refreshTokenHash, Instant expiresAt) {
        return new UserSession(id, userId, refreshTokenHash, expiresAt);
    }

    public boolean isUsable(Instant now) {
        return now.isBefore(expiresAt);
    }

    public void rotate(String refreshTokenHash, Instant expiresAt) {
        this.refreshTokenHash = refreshTokenHash;
        this.expiresAt = expiresAt;
        this.lastRotatedAt = Instant.now();
    }
}
