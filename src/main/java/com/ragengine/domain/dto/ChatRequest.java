package com.ragengine.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record ChatRequest(
        @NotBlank(message = "Question cannot be blank")
        String question,

        @NotEmpty(message = "At least one document ID is required")
        List<UUID> documentIds,

        UUID conversationId
) {}
