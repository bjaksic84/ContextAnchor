package com.ragengine.domain.dto;

import com.ragengine.domain.entity.DocumentStatus;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record DocumentResponse(
        UUID id,
        String originalName,
        String contentType,
        Long fileSize,
        Integer pageCount,
        DocumentStatus status,
        String errorMessage,
        Integer chunkCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
