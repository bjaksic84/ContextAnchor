package com.ragengine.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RateLimitService.
 * Validates token-bucket rate limiting behaviour per tenant.
 */
class RateLimitServiceTest {

    private RateLimitService rateLimitService;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        RateLimitConfig config = new RateLimitConfig();
        config.setEnabled(true);
        config.setRequestsPerMinute(5);
        config.setChatRequestsPerMinute(3);
        config.setUploadsPerHour(2);
        rateLimitService = new RateLimitService(config);
    }

    @Test
    @DisplayName("Should allow requests within API rate limit")
    void shouldAllowRequestsWithinLimit() {
        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimitService.tryConsumeApiRequest(tenantId),
                    "Request " + (i + 1) + " should be allowed");
        }
    }

    @Test
    @DisplayName("Should reject requests exceeding API rate limit")
    void shouldRejectExceedingApiLimit() {
        for (int i = 0; i < 5; i++) {
            rateLimitService.tryConsumeApiRequest(tenantId);
        }
        assertFalse(rateLimitService.tryConsumeApiRequest(tenantId),
                "6th request should be rejected");
    }

    @Test
    @DisplayName("Should allow chat requests within limit")
    void shouldAllowChatRequestsWithinLimit() {
        for (int i = 0; i < 3; i++) {
            assertTrue(rateLimitService.tryConsumeChatRequest(tenantId));
        }
    }

    @Test
    @DisplayName("Should reject chat requests exceeding limit")
    void shouldRejectExceedingChatLimit() {
        for (int i = 0; i < 3; i++) {
            rateLimitService.tryConsumeChatRequest(tenantId);
        }
        assertFalse(rateLimitService.tryConsumeChatRequest(tenantId));
    }

    @Test
    @DisplayName("Should allow upload requests within limit")
    void shouldAllowUploadRequestsWithinLimit() {
        for (int i = 0; i < 2; i++) {
            assertTrue(rateLimitService.tryConsumeUploadRequest(tenantId));
        }
    }

    @Test
    @DisplayName("Should reject upload requests exceeding limit")
    void shouldRejectExceedingUploadLimit() {
        for (int i = 0; i < 2; i++) {
            rateLimitService.tryConsumeUploadRequest(tenantId);
        }
        assertFalse(rateLimitService.tryConsumeUploadRequest(tenantId));
    }

    @Test
    @DisplayName("Should isolate rate limits between tenants")
    void shouldIsolateTenantRateLimits() {
        UUID tenant1 = UUID.randomUUID();
        UUID tenant2 = UUID.randomUUID();

        // Exhaust tenant1's limit
        for (int i = 0; i < 5; i++) {
            rateLimitService.tryConsumeApiRequest(tenant1);
        }
        assertFalse(rateLimitService.tryConsumeApiRequest(tenant1));

        // Tenant2 should still have budget
        assertTrue(rateLimitService.tryConsumeApiRequest(tenant2));
    }

    @Test
    @DisplayName("Should report correct remaining tokens")
    void shouldReportRemainingTokens() {
        assertEquals(5, rateLimitService.getRemainingApiTokens(tenantId));

        rateLimitService.tryConsumeApiRequest(tenantId);
        rateLimitService.tryConsumeApiRequest(tenantId);

        assertEquals(3, rateLimitService.getRemainingApiTokens(tenantId));
    }

    @Test
    @DisplayName("Should bypass rate limiting when disabled")
    void shouldBypassWhenDisabled() {
        RateLimitConfig disabledConfig = new RateLimitConfig();
        disabledConfig.setEnabled(false);
        disabledConfig.setRequestsPerMinute(1);
        RateLimitService disabled = new RateLimitService(disabledConfig);

        // Should always return true regardless of how many requests
        for (int i = 0; i < 100; i++) {
            assertTrue(disabled.tryConsumeApiRequest(tenantId));
        }
    }

    @Test
    @DisplayName("Should isolate API, chat, and upload buckets")
    void shouldIsolateBucketTypes() {
        // Exhaust API bucket
        for (int i = 0; i < 5; i++) {
            rateLimitService.tryConsumeApiRequest(tenantId);
        }
        assertFalse(rateLimitService.tryConsumeApiRequest(tenantId));

        // Chat and upload should still work
        assertTrue(rateLimitService.tryConsumeChatRequest(tenantId));
        assertTrue(rateLimitService.tryConsumeUploadRequest(tenantId));
    }
}
