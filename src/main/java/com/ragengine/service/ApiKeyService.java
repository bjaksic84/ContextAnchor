package com.ragengine.service;

import com.ragengine.audit.AuditAction;
import com.ragengine.audit.AuditService;
import com.ragengine.domain.entity.ApiKey;
import com.ragengine.domain.entity.User;
import com.ragengine.repository.ApiKeyRepository;
import com.ragengine.security.SecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for managing API keys.
 * 
 * API keys provide an alternative to JWT for programmatic access.
 * Keys are generated as random strings, hashed with SHA-256 for storage,
 * and only shown once at creation time.
 * 
 * Format: ctx_<32 random hex chars> (e.g., ctx_a1b2c3d4e5f6...)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final SecurityContext securityContext;
    private final AuditService auditService;

    private static final String KEY_PREFIX = "ctx_";
    private static final int KEY_LENGTH = 32; // 32 hex chars = 128 bits

    /**
     * Creates a new API key for the current user.
     * Returns a response containing the raw key — this is the only time it's visible.
     *
     * @param name       human-readable name for the key
     * @param expiresAt  optional expiration date (null = never expires)
     * @return map with "key" (raw), "id", "name", "prefix", "createdAt"
     */
    @Transactional
    public Map<String, Object> createApiKey(String name, LocalDateTime expiresAt) {
        User user = securityContext.getCurrentUser();

        // Generate cryptographically secure random key
        String rawKey = KEY_PREFIX + generateRandomHex(KEY_LENGTH);
        String hash = sha256(rawKey);
        String prefix = rawKey.substring(0, KEY_PREFIX.length() + 8) + "...";

        ApiKey apiKey = ApiKey.builder()
                .name(name)
                .keyHash(hash)
                .keyPrefix(prefix)
                .tenant(user.getTenant())
                .user(user)
                .expiresAt(expiresAt)
                .build();

        apiKey = apiKeyRepository.save(apiKey);

        auditService.logAction(AuditAction.API_KEY_CREATED, "API_KEY",
                apiKey.getId(), "Key '" + name + "' created");

        log.info("API key created: {} for user {}", name, user.getEmail());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", apiKey.getId());
        response.put("name", apiKey.getName());
        response.put("key", rawKey); // Only shown once!
        response.put("prefix", prefix);
        response.put("expiresAt", expiresAt);
        response.put("createdAt", apiKey.getCreatedAt());
        return response;
    }

    /**
     * Lists all active API keys for the current tenant (without the raw key).
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listApiKeys() {
        UUID tenantId = securityContext.getCurrentTenantId();
        return apiKeyRepository.findByTenantIdAndActiveTrue(tenantId).stream()
                .map(key -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", key.getId());
                    map.put("name", key.getName());
                    map.put("prefix", key.getKeyPrefix());
                    map.put("expiresAt", key.getExpiresAt());
                    map.put("lastUsedAt", key.getLastUsedAt());
                    map.put("createdAt", key.getCreatedAt());
                    return map;
                })
                .toList();
    }

    /**
     * Revokes (soft-deletes) an API key.
     */
    @Transactional
    public void revokeApiKey(UUID keyId) {
        UUID tenantId = securityContext.getCurrentTenantId();
        ApiKey apiKey = apiKeyRepository.findByIdAndTenantId(keyId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("API key not found"));

        apiKey.setActive(false);
        apiKeyRepository.save(apiKey);

        auditService.logAction(AuditAction.API_KEY_REVOKED, "API_KEY",
                keyId, "Key '" + apiKey.getName() + "' revoked");

        log.info("API key revoked: {} ({})", apiKey.getName(), keyId);
    }

    /**
     * Validates a raw API key and returns the associated user.
     * Also updates last-used timestamp.
     *
     * @param rawKey the raw API key string
     * @return Optional containing the user if the key is valid
     */
    @Transactional
    public Optional<User> validateApiKey(String rawKey) {
        String hash = sha256(rawKey);
        return apiKeyRepository.findByKeyHashAndActiveTrue(hash)
                .filter(key -> !key.isExpired())
                .map(key -> {
                    // Update last used timestamp
                    key.setLastUsedAt(LocalDateTime.now());
                    apiKeyRepository.save(key);
                    return key.getUser();
                });
    }

    // ============================
    // Private helpers
    // ============================

    private String generateRandomHex(int length) {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[length / 2];
        random.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
