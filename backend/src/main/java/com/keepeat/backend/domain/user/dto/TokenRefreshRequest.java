package com.keepeat.backend.domain.user.dto;

import lombok.Getter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Getter
public class TokenRefreshRequest {
    @NotBlank(message = "Refresh Token은 필수입니다.")
    @Size(max = 4096, message = "Refresh Token 형식이 올바르지 않습니다.")
    private String refreshToken;
}
