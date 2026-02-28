package com.ragengine.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the ChunkingService.
 * Tests the text chunking logic without requiring Spring context.
 */
class ChunkingServiceTest {

    private final ChunkingService chunkingService = new ChunkingService(800, 200, 100);

    @Test
    @DisplayName("Should return empty list for null input")
    void shouldReturnEmptyForNull() {
        List<String> chunks = chunkingService.chunkText(null);
        assertTrue(chunks.isEmpty());
    }

    @Test
    @DisplayName("Should return empty list for blank input")
    void shouldReturnEmptyForBlank() {
        List<String> chunks = chunkingService.chunkText("   ");
        assertTrue(chunks.isEmpty());
    }

    @Test
    @DisplayName("Should return single chunk for short text")
    void shouldReturnSingleChunkForShortText() {
        String text = "This is a short piece of text. It should fit in a single chunk. " +
                "There is no need to split this into multiple chunks because it's small enough.";
        List<String> chunks = chunkingService.chunkText(text);
        assertEquals(1, chunks.size());
        assertFalse(chunks.getFirst().isBlank());
    }

    @Test
    @DisplayName("Should split long text into multiple chunks")
    void shouldSplitLongText() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            sb.append("This is sentence number ").append(i)
                    .append(" which contains some meaningful content about the topic. ");
        }

        List<String> chunks = chunkingService.chunkText(sb.toString());
        assertTrue(chunks.size() > 1, "Expected multiple chunks but got " + chunks.size());

        // Verify no chunk is empty
        for (String chunk : chunks) {
            assertFalse(chunk.isBlank(), "Chunk should not be blank");
        }
    }

    @Test
    @DisplayName("Should maintain overlap between chunks")
    void shouldMaintainOverlap() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++) {
            sb.append("Sentence ").append(i).append(" contains unique content about topic ")
                    .append(i).append(". ");
        }

        List<String> chunks = chunkingService.chunkText(sb.toString());
        assertTrue(chunks.size() > 2, "Need at least 3 chunks to test overlap");

        // Check that consecutive chunks share some content (overlap)
        for (int i = 0; i < chunks.size() - 1; i++) {
            String current = chunks.get(i);
            String next = chunks.get(i + 1);

            // The end of current chunk should appear in the beginning of next chunk
            // due to overlap. Find the last sentence of current chunk.
            String lastSentence = current.substring(current.lastIndexOf("Sentence"));
            String firstPart = lastSentence.split("\\.")[0];

            assertTrue(next.contains(firstPart),
                    "Chunk " + (i + 1) + " should overlap with chunk " + i);
        }
    }

    @Test
    @DisplayName("Should estimate token count correctly")
    void shouldEstimateTokenCount() {
        // ~4 chars per token
        String text = "Hello world test"; // 16 chars → ~4 tokens
        int tokens = chunkingService.estimateTokenCount(text);
        assertEquals(4, tokens);
    }

    @Test
    @DisplayName("Should return 0 tokens for null/blank text")
    void shouldReturnZeroTokensForEmpty() {
        assertEquals(0, chunkingService.estimateTokenCount(null));
        assertEquals(0, chunkingService.estimateTokenCount(""));
        assertEquals(0, chunkingService.estimateTokenCount("   "));
    }

    @Test
    @DisplayName("Should handle text without sentence boundaries")
    void shouldHandleTextWithoutSentences() {
        // Text with no periods, just a long run-on
        String text = "word ".repeat(500); // 2500 chars, no sentence boundaries
        List<String> chunks = chunkingService.chunkText(text);
        assertTrue(chunks.size() > 1, "Should split even without sentence boundaries");
    }
}
