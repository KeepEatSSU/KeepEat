package com.keepeat.backend.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

public record OAuthCodeExchangeRequest(
        @NotBlank(message = "OAuth 로그인 코드를 입력해 주세요.")
        String code
) {}
