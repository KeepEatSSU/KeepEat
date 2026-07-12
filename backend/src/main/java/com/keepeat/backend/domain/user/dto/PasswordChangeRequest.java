package com.keepeat.backend.domain.user.dto; // 본인 패키지 경로에 맞게 수정

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PasswordChangeRequest(
        @NotBlank(message = "현재 비밀번호를 입력해 주세요.")
        String currentPassword,

        @NotBlank(message = "새로운 비밀번호를 입력해 주세요.")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,15}$",
                message = "비밀번호는 8자리 이상 16자리 미만이며, 영문, 숫자, 특수문자를 모두 포함해야 합니다."
        )
        String newPassword
) {}
