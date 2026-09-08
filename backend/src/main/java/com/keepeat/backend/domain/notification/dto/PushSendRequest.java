package com.keepeat.backend.domain.notification.dto;

import com.keepeat.backend.domain.notification.entity.NotificationType;

public record PushSendRequest(
        String targetToken,        // 누구에게 보낼지
        String title,              // 제목
        String body,               // 내용
        NotificationType type,     // 목적지 화면 타입 (data 주머니용)
        Long notificationId        // DB notification ID (탭 시 단건 읽음 처리용)
) {}
