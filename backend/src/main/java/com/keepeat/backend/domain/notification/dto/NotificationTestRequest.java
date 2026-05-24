package com.keepeat.backend.domain.notification.dto;

import com.keepeat.backend.domain.notification.entity.NotificationType;
import jakarta.validation.constraints.NotNull;

public record NotificationTestRequest( // 자기 자신(JWT의 userId)에게만 발사
        @NotNull(message = "type은 필수입니다.")
        NotificationType type, // type: 필수. 어떤 종류의 알림을 발사할지.

        String title, // title: 선택. 비우면 타입별 기본 제목 사용.

        String message // message: 선택. 비우면 타입별 기본 본문 사용.
) {}
