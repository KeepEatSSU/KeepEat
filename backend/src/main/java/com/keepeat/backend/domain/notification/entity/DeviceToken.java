package com.keepeat.backend.domain.notification.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "device_token", indexes = {
        @Index(name = "idx_device_token_user_id", columnList = "user_id"),
        @Index(name = "idx_device_token_session_id", columnList = "session_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "session_id", length = 36)
    private String sessionId;

    @Column(nullable = false, unique = true)
    private String token; // 기기 고유 주소

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public DeviceToken(Long userId, String token) {
        this(userId, null, token);
    }

    public DeviceToken(Long userId, String sessionId, String token) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.token = token;
        this.createdAt = LocalDateTime.now();
    }

}
