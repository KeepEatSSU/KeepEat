package com.keepeat.backend.domain.user.dto;

import lombok.Getter;

@Getter
public class SignUpRequest {
    private String email;
    private String password;
}
