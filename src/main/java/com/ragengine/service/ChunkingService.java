package com.ragengine.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service responsible for splitting extracted text into overlapping chunks.
 * Uses a sentence-aware chunking strategy to avoid breaking mid-sentence.
 * 
 * Chunking strategy:
 * - Splits by sentences first, then groups sentences into chunks of target size
 * - Maintains overlap between consecutive chunks for context continuity
 * - Filters out chunks below minimum size threshold
 */
@Service
@Slf4j
public class ChunkingService {

    private final int chunkSize;
    private final int chunkOverlap;
    private final int minChunkSize;

    public ChunkingService(
            @Value("${rag.chunking.chunk-size:800}") int chunkSize,
            @Value("${rag.chunking.chunk-overlap:200}") int chunkOverlap,
            @Value("${rag.chunking.min-chunk-size:100}") int minChunkSize
    ) {
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
        this.minChunkSize = minChunkSize;
    }

    /**
     * Splits text into overlapping chunks using sentence-aware boundaries.
     *
     * @param text the full text to chunk
     * @return list of text chunks
     */
    public List<String> chunkText(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        // Normalize whitespace
        text = text.replaceAll("\\s+", " ").trim();

        // Split into sentences
        List<String> sentences = splitIntoSentences(text);
        log.debug("Split text into {} sentences", sentences.size());

        // Group sentences into chunks with overlap
        List<String> chunks = groupSentencesIntoChunks(sentences);
        log.info("Created {} chunks from {} characters of text", chunks.size(), text.length());

        return chunks;
    }

    /**
     * Splits text into sentences using common sentence-ending patterns.
     */
    private List<String> splitIntoSentences(String text) {
        List<String> sentences = new ArrayList<>();

        // Split on sentence boundaries: period, question mark, exclamation mark
        // followed by whitespace and an uppercase letter (to avoid splitting on abbreviations)
        String[] rawSentences = text.split("(?<=[.!?])\\s+(?=[A-Z])");

        for (String sentence : rawSentences) {
            String trimmed = sentence.trim();
            if (!trimmed.isEmpty()) {
                sentences.add(trimmed);
            }
        }

        // If no sentence boundaries found, split by newlines or fixed size
        if (sentences.size() <= 1 && text.length() > chunkSize) {
            return splitByFixedSize(text);
        }

        return sentences;
    }

    /**
     * Groups sentences into chunks of approximately chunkSize characters,
     * with overlap between consecutive chunks.
     */
    private List<String> groupSentencesIntoChunks(List<String> sentences) {
        List<String> chunks = new ArrayList<>();

        if (sentences.isEmpty()) {
            return chunks;
        }

        StringBuilder currentChunk = new StringBuilder();
        int sentenceStart = 0;

        for (int i = 0; i < sentences.size(); i++) {
            String sentence = sentences.get(i);

            // If adding this sentence exceeds chunk size and we have content
            if (currentChunk.length() + sentence.length() > chunkSize && currentChunk.length() > 0) {
                // Save current chunk
                String chunk = currentChunk.toString().trim();
                if (chunk.length() >= minChunkSize) {
                    chunks.add(chunk);
                }

                // Calculate overlap: go back to find the sentence that starts the overlap
                currentChunk = new StringBuilder();
                int overlapLength = 0;
                int overlapStart = i;

                // Walk backward to build overlap
                for (int j = i - 1; j >= sentenceStart; j--) {
                    overlapLength += sentences.get(j).length() + 1; // +1 for space
                    if (overlapLength >= chunkOverlap) {
                        overlapStart = j;
                        break;
                    }
                    overlapStart = j;
                }

                // Add overlap sentences to new chunk
                for (int j = overlapStart; j < i; j++) {
                    currentChunk.append(sentences.get(j)).append(" ");
                }

                sentenceStart = overlapStart;
            }

            currentChunk.append(sentence).append(" ");
        }

        // Don't forget the last chunk
        String lastChunk = currentChunk.toString().trim();
        if (lastChunk.length() >= minChunkSize) {
            chunks.add(lastChunk);
        }

        return chunks;
    }

    /**
     * Fallback: splits text by fixed size with overlap when no sentence boundaries found.
     */
    private List<String> splitByFixedSize(String text) {
        List<String> chunks = new ArrayList<>();
        int start = 0;

        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());

            // Try to break at a word boundary
            if (end < text.length()) {
                int lastSpace = text.lastIndexOf(' ', end);
                if (lastSpace > start) {
                    end = lastSpace;
                }
            }

            String chunk = text.substring(start, end).trim();
            if (chunk.length() >= minChunkSize) {
                chunks.add(chunk);
            }

            // If we've reached the end, stop
            if (end >= text.length()) break;

            // Advance with overlap, but always move forward by at least 1
            int nextStart = end - chunkOverlap;
            if (nextStart <= start) {
                nextStart = start + Math.max(chunkSize / 2, 1);
            }
            start = nextStart;
        }

        return chunks;
    }

    /**
     * Estimates the token count for a piece of text.
     * Rough approximation: ~4 characters per token for English text.
     *
     * @param text the text to estimate tokens for
     * @return estimated token count
     */
    public int estimateTokenCount(String text) {
        if (text == null || text.isBlank()) return 0;
        return (int) Math.ceil(text.length() / 4.0);
    }
}
