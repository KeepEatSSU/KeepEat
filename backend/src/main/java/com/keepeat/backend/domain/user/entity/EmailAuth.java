package com.keepeat.backend.domain.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "email_auth", indexes = {
        @Index(name = "idx_email_auth_expired_at", columnList = "expired_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailAuth {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 320) // 이메일당 최신 인증번호 1개만 유지
    private String email;

    @Column(nullable = false)
    private String authCode;

    @Column(name = "expired_at", nullable = false)
    private LocalDateTime expiredAt; // 만료 시간

    @Column(nullable = false)
    private boolean isVerified = false; // 인증 성공 여부

    @Column(nullable = false)
    private int failedAttempts = 0;


    @Builder
    public EmailAuth(String email, String authCode, LocalDateTime expiredAt) {
        this.email = email;
        this.authCode = authCode;
        this.expiredAt = expiredAt;
    }

    // 새 인증번호 재발급 시 업데이트하는 메서드
    public void updateAuthCode(String newAuthCode, LocalDateTime newExpiredAt) {
        this.authCode = newAuthCode;
        this.expiredAt = newExpiredAt;
        this.isVerified = false;
        this.failedAttempts = 0;
    }

    // 인증 성공 처리 메서드
    public void verify() {
        this.isVerified = true;
        this.failedAttempts = 0;
    }

    public void recordFailure() {
        this.failedAttempts++;
    }

    public boolean isExpired(LocalDateTime now) {
        return now.isAfter(this.expiredAt);
    }
}
