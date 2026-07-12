package com.keepeat.backend.domain.notification.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "device_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true)
    private String token; // 기기 고유 주소

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public DeviceToken(Long userId, String token) {
        this.userId = userId;
        this.token = token;
        this.createdAt = LocalDateTime.now();
    }

    public void reassignTo(Long userId) {
        this.userId = userId;
    }
}
