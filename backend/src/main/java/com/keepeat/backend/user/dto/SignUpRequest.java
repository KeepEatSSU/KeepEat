package com.keepeat.backend.user.dto;

import lombok.Getter;
import org.jspecify.annotations.Nullable;

@Getter
public class SignUpRequest {
    private String email;
    private String password;
}
