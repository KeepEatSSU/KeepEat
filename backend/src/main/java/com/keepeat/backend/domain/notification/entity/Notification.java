package com.keepeat.backend.domain.notification.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId; // 알림을 받을 유저의 ID

    @Column(nullable = false)
    private String title; // 알림 제목

    @Column(nullable = false)
    private String body; // 알림 내용

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType notificationType; // 알림 종류 (목적지 화면)

    @Column
    private String targetId; // 목적지 아이템의 DB ID (예: OCR 결과 ID, 식재료 ID)

    @Column(nullable = false)
    private boolean isRead = false; // 읽음 여부

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public Notification(Long userId, String title, String body, NotificationType notificationType, String targetId) {
        this.userId = userId;
        this.title = title;
        this.body = body;
        this.notificationType = notificationType;
        this.targetId = targetId;
        this.createdAt = LocalDateTime.now();
    }

    // 알림 읽음 처리 메서드
    public void markAsRead() {
        this.isRead = true;
    }
}