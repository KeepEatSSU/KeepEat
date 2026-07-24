package com.keepeat.backend.domain.user.dto;

import jakarta.validation.constraints.NotNull;

public record NotificationSettingRequest(
        @NotNull(message = "알림 수신 여부를 입력해 주세요.")
        Boolean enabled
) {
}
