package com.keepeat.backend.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.keepeat.backend.domain.user.entity.Role;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserResponse {
    private Long id;
    private String email;
    private String nickname;
    private Role role;
    private String provider;

    @Getter(AccessLevel.NONE)
    private boolean isNotificationEnabled;

    @JsonProperty("isNotificationEnabled")
    public boolean isNotificationEnabled() {
        return isNotificationEnabled;
    }
}
