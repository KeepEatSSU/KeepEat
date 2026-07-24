package com.keepeat.backend.domain.user.dto;

import jakarta.validation.constraints.Size;

/**
 * 회원 탈퇴 요청 DTO.
 *
 * LOCAL 가입자는 본인 확인을 위해 현재 비밀번호 필수.
 * OAuth(GOOGLE 등) 가입자는 password 필드 없이 호출 가능 — body 자체가 비어있어도 됨.
 */
public record UserDeleteRequest(
        @Size(max = 72, message = "비밀번호 형식이 올바르지 않습니다.")
        String password
) {}
