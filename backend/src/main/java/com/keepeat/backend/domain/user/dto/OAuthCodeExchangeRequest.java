package com.keepeat.backend.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OAuthCodeExchangeRequest(
        @NotBlank(message = "OAuth 로그인 코드를 입력해 주세요.")
        @Size(max = 256, message = "OAuth 로그인 코드 형식이 올바르지 않습니다.")
        String code
) {}
