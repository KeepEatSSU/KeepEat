package com.keepeat.backend.domain.user.dto;

import jakarta.validation.constraints.Size;

public record LogoutRequest(
        @Size(max = 4096, message = "Refresh Token 형식이 올바르지 않습니다.")
        String refreshToken,

        @Size(max = 255, message = "기기 토큰 형식이 올바르지 않습니다.")
        String deviceToken
) {
}
