package com.keepeat.backend.domain.notification.dto;

import com.keepeat.backend.domain.notification.entity.NotificationType;

public record PushSendRequest(
        String targetToken,        // 누구에게 보낼지
        String title,              // 제목
        String body,               // 내용
        NotificationType type,     // 목적지 화면 타입 (data 주머니용)
        String targetId            // 목적지 아이템 ID (data 주머니용)
) {}