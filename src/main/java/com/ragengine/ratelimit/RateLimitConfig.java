package com.ragengine.ratelimit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for tenant-based rate limiting.
 * Each tenant gets a separate token bucket.
 */
@Configuration
@ConfigurationProperties(prefix = "rag.rate-limit")
@Getter
@Setter
public class RateLimitConfig {

    /**
     * Maximum number of API requests per window period.
     */
    private int requestsPerMinute = 60;

    /**
     * Maximum number of chat (RAG) requests per window period.
     */
    private int chatRequestsPerMinute = 20;

    /**
     * Maximum number of document uploads per hour.
     */
    private int uploadsPerHour = 30;

    /**
     * Whether rate limiting is enabled globally.
     */
    private boolean enabled = true;
}
