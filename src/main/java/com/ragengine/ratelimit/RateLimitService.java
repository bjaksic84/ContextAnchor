package com.ragengine.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-tenant rate limiting using Bucket4j token buckets.
 * 
 * Each tenant gets separate buckets for:
 * - General API requests (e.g., 60/min)
 * - Chat/RAG requests (e.g., 20/min)
 * - Document uploads (e.g., 30/hour)
 * 
 * Buckets are created lazily and stored in a ConcurrentHashMap.
 * In production, this could be backed by Redis for distributed rate limiting.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RateLimitService {

    private final RateLimitConfig config;

    /** tenantId -> bucket for general API requests */
    private final Map<UUID, Bucket> apiBuckets = new ConcurrentHashMap<>();

    /** tenantId -> bucket for chat/RAG requests */
    private final Map<UUID, Bucket> chatBuckets = new ConcurrentHashMap<>();

    /** tenantId -> bucket for document uploads */
    private final Map<UUID, Bucket> uploadBuckets = new ConcurrentHashMap<>();

    /**
     * Checks and consumes a general API request token.
     *
     * @param tenantId the tenant making the request
     * @return true if the request is allowed, false if rate-limited
     */
    public boolean tryConsumeApiRequest(UUID tenantId) {
        if (!config.isEnabled()) return true;
        Bucket bucket = apiBuckets.computeIfAbsent(tenantId, this::createApiBucket);
        boolean consumed = bucket.tryConsume(1);
        if (!consumed) {
            log.warn("Rate limit exceeded for tenant {} on API requests", tenantId);
        }
        return consumed;
    }

    /**
     * Checks and consumes a chat request token (stricter limit).
     *
     * @param tenantId the tenant making the request
     * @return true if the request is allowed, false if rate-limited
     */
    public boolean tryConsumeChatRequest(UUID tenantId) {
        if (!config.isEnabled()) return true;
        Bucket bucket = chatBuckets.computeIfAbsent(tenantId, this::createChatBucket);
        boolean consumed = bucket.tryConsume(1);
        if (!consumed) {
            log.warn("Rate limit exceeded for tenant {} on chat requests", tenantId);
        }
        return consumed;
    }

    /**
     * Checks and consumes a document upload token.
     *
     * @param tenantId the tenant making the request
     * @return true if the upload is allowed, false if rate-limited
     */
    public boolean tryConsumeUploadRequest(UUID tenantId) {
        if (!config.isEnabled()) return true;
        Bucket bucket = uploadBuckets.computeIfAbsent(tenantId, this::createUploadBucket);
        boolean consumed = bucket.tryConsume(1);
        if (!consumed) {
            log.warn("Rate limit exceeded for tenant {} on document uploads", tenantId);
        }
        return consumed;
    }

    /**
     * Gets the number of remaining API request tokens for a tenant.
     */
    public long getRemainingApiTokens(UUID tenantId) {
        Bucket bucket = apiBuckets.get(tenantId);
        return bucket != null ? bucket.getAvailableTokens() : config.getRequestsPerMinute();
    }

    // ============================
    // Bucket factories
    // ============================

    private Bucket createApiBucket(UUID tenantId) {
        return Bucket.builder()
                .addLimit(Bandwidth.simple(config.getRequestsPerMinute(), Duration.ofMinutes(1)))
                .build();
    }

    private Bucket createChatBucket(UUID tenantId) {
        return Bucket.builder()
                .addLimit(Bandwidth.simple(config.getChatRequestsPerMinute(), Duration.ofMinutes(1)))
                .build();
    }

    private Bucket createUploadBucket(UUID tenantId) {
        return Bucket.builder()
                .addLimit(Bandwidth.simple(config.getUploadsPerHour(), Duration.ofHours(1)))
                .build();
    }
}
