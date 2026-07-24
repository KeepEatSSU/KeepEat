package com.keepeat.backend.domain.notification.dto;

import com.keepeat.backend.domain.notification.entity.Notification;
import com.keepeat.backend.domain.notification.entity.NotificationType;
import java.time.Instant;

public record NotificationResponse(
        Long id,
        String title,
        String body,
        NotificationType type,
        String targetId,
        boolean isRead,
        Instant createdAt
) {
    // Entity를 DTO로 변환하는 편의 메서드
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getTitle(),
                notification.getBody(),
                notification.getNotificationType(),
                notification.getTargetId(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}
