package com.keepeat.backend.domain.notification.dto;

import jakarta.validation.constraints.NotBlank;

public record DeviceTokenRequest(
        @NotBlank(message = "기기 푸시 토큰은 필수입니다.")
        String token
) {}