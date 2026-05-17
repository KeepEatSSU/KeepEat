package com.keepeat.backend.domain.user.dto; // 본인 패키지 경로에 맞게 수정

import jakarta.validation.constraints.NotBlank;

public record PasswordChangeRequest(
        @NotBlank(message = "현재 비밀번호를 입력해 주세요.")
        String currentPassword,

        @NotBlank(message = "새로운 비밀번호를 입력해 주세요.")
        String newPassword
) {}