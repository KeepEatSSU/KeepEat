package com.keepeat.backend.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor // 롬복: 필드값을 모두 넣을 수 있는 생성자를 자동으로 만들어줍니다.
public class TokenResponse {
    private String accessToken;
    private String refreshToken;
}