package com.keepeat.backend.domain.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DeviceTokenRequest(
        @NotBlank(message = "기기 푸시 토큰은 필수입니다.")
        @Size(max = 255, message = "기기 푸시 토큰은 255자 이하여야 합니다.")
        @Pattern(
                regexp = "^(Expo(nent)?PushToken)\\[[A-Za-z0-9_-]+]$",
                message = "올바른 Expo 푸시 토큰 형식이 아닙니다."
        )
        String token
) {}
