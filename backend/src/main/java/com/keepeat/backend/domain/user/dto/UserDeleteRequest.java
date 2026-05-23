package com.keepeat.backend.domain.user.dto;

/**
 * 회원 탈퇴 요청 DTO.
 *
 * LOCAL 가입자는 본인 확인을 위해 현재 비밀번호 필수.
 * OAuth(GOOGLE 등) 가입자는 password 필드 없이 호출 가능 — body 자체가 비어있어도 됨.
 */
public record UserDeleteRequest(
        String password
) {}
