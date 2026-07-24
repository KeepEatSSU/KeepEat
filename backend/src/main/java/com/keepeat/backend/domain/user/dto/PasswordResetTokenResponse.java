package com.keepeat.backend.domain.user.dto;

public record PasswordResetTokenResponse(
        String resetToken,
        long expiresInSeconds
) {
}
