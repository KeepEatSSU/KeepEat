package com.keepeat.backend.domain.user.dto;

import com.keepeat.backend.domain.user.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserResponse {
    private Long id;
    private String email;
    private Role role;
}
