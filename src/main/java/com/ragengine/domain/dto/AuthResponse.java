package com.ragengine.domain.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserInfo user
) {
    @Builder
    public record UserInfo(
            UUID id,
            String email,
            String fullName,
            String role,
            UUID tenantId,
            String tenantName
    ) {}
}
