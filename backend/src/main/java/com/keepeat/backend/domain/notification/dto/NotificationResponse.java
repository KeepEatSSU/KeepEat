package com.keepeat.backend.domain.notification.dto;

import com.keepeat.backend.domain.notification.entity.Notification;
import com.keepeat.backend.domain.notification.entity.NotificationType;
import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        String title,
        String body,
        NotificationType type,
        String targetId,
        boolean isRead,
        LocalDateTime createdAt
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