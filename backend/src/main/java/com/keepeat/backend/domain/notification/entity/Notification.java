package com.keepeat.backend.domain.notification.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Entity
@Table(name = "notification", indexes = {
        @Index(name = "idx_notification_user_cursor", columnList = "user_id,id"),
        @Index(name = "idx_notification_user_unread", columnList = "user_id,is_read"),
        @Index(name = "idx_notification_user_type_unread", columnList = "user_id,notification_type,is_read"),
        @Index(name = "idx_notification_created_at", columnList = "created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId; // 알림을 받을 유저의 ID

    @Column(nullable = false, length = 200)
    private String title; // 알림 제목

    @Column(nullable = false, length = 1000)
    private String body; // 알림 내용

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false)
    private NotificationType notificationType; // 알림 종류 (목적지 화면)

    @Column
    private String targetId; // 목적지 아이템의 DB ID (예: OCR 결과 ID, 식재료 ID)

    @Column(unique = true, length = 160)
    private String dedupeKey;

    @Column(name = "is_read", nullable = false)
    private boolean isRead = false; // 읽음 여부

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamp with time zone")
    private Instant createdAt;

    @Builder
    public Notification(Long userId, String title, String body, NotificationType notificationType, String targetId) {
        this(userId, title, body, notificationType, targetId, null);
    }

    public Notification(Long userId, String title, String body, NotificationType notificationType, String targetId, String dedupeKey) {
        this.userId = userId;
        this.title = title;
        this.body = body;
        this.notificationType = notificationType;
        this.targetId = targetId;
        this.dedupeKey = dedupeKey;
        this.createdAt = Instant.now();
    }

    // 알림 읽음 처리 메서드
    public void markAsRead() {
        this.isRead = true;
    }
}
