package com.keepeat.backend.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(
        @NotBlank(message = "비밀번호 재설정 토큰을 입력해 주세요.")
        @Size(max = 256, message = "비밀번호 재설정 토큰 형식이 올바르지 않습니다.")
        String resetToken,

        @NotBlank(message = "새로운 비밀번호를 입력해 주세요.")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,15}$",
                message = "비밀번호는 8자리 이상 16자리 미만이며, 영문, 숫자, 특수문자를 모두 포함해야 합니다."
        )
        String newPassword
) {
}
