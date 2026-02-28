package com.ragengine.domain.dto;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Builder
public record ConversationResponse(
        UUID id,
        String title,
        List<MessageResponse> messages,
        List<UUID> documentIds,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    @Builder
    public record MessageResponse(
            UUID id,
            String role,
            String content,
            LocalDateTime createdAt
    ) {}
}
