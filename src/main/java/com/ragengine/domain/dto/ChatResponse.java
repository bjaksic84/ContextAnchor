package com.ragengine.domain.dto;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Builder
public record ChatResponse(
        UUID conversationId,
        String answer,
        List<Source> sources,
        LocalDateTime timestamp
) {
    @Builder
    public record Source(
            UUID documentId,
            String documentName,
            String chunkContent,
            Integer chunkIndex,
            Integer pageNumber,
            Double similarityScore
    ) {}
}
