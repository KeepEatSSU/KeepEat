package com.keepeat.backend.domain.user.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import java.time.LocalDateTime;

@Entity
@Table(name = "app_User", uniqueConstraints = {
        @UniqueConstraint(name = "uk_app_user_provider_identity", columnNames = {"provider", "provider_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLDelete(sql = "UPDATE app_user SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // BIGINT에 매칭됨

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(nullable  = false)
    private String nickname;

    @Column(nullable = true)
    private String password; // 암호화된 긴 문자열이 들어갈 자리

    @Enumerated(EnumType.STRING) // Enum 이름을 DB에 문자열로 저장
    @Column(nullable = false)
    private Role role;

    @Column(length = 512)
    private String refreshToken;

    private String provider;    // "google", "kakao" 등
    @Column(name = "provider_id")
    private String providerId;

    // 알람 수신 동의 여부
    @Column(nullable = false)
    private boolean isNotificationEnabled = true; // 기본값은 알림 켜짐(true)

    // 로그아웃을 위한 메서드
    public void clearRefreshToken() {
        this.refreshToken = null;
    }

    // 일반 회원가입용
    public AppUser(String nickname, String email, String password, Role role) {
        this.nickname = nickname;
        this.email = email;
        this.password = password;
        this.role = role;
        this.provider = "LOCAL";
    }

    // 소셜 로그인용
    public AppUser(String nickname, String email, String provider, String providerId, Role role) {
        this.nickname = nickname;
        this.email = email;
        this.password = null;
        this.provider = provider;
        this.providerId = providerId;
        this.role = role;
    }

    // 알림 설정 토글 메서드
    private LocalDateTime deletedAt;

    public void setNotificationEnabled(boolean status) {
        this.isNotificationEnabled = status;
    }

    public void updatePassword(String newPassword) {
        this.password = newPassword;
    }

    public void attachOAuthIdentity(String provider, String providerId) {
        this.provider = provider;
        this.providerId = providerId;
    }

    public void markAsDeleted() {
        this.email = "deleted_" + java.util.UUID.randomUUID() + "@deleted.invalid";
        this.nickname = "탈퇴한 사용자";
        this.password = null;
        this.provider = "DELETED";
        this.providerId = null;
        this.refreshToken = null;
        this.isNotificationEnabled = false;
    }
}
