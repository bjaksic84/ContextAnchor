package com.ragengine.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * API key entity for programmatic access.
 * Alternative to JWT for server-to-server or CI/CD integrations.
 * 
 * Each key is scoped to a specific user + tenant and can be individually revoked.
 */
@Entity
@Table(name = "api_keys", indexes = {
        @Index(name = "idx_api_key_hash", columnList = "key_hash", unique = true),
        @Index(name = "idx_api_key_tenant", columnList = "tenant_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Human-readable name for the key (chosen by the user).
     */
    @Column(nullable = false, length = 200)
    private String name;

    /**
     * SHA-256 hash of the actual key value.
     * The raw key is only shown once at creation time.
     */
    @Column(name = "key_hash", nullable = false, unique = true, length = 64)
    private String keyHash;

    /**
     * Prefix for display purposes (e.g., "ctx_a1b2c3...").
     * Allows users to identify keys without exposing the full value.
     */
    @Column(name = "key_prefix", nullable = false, length = 20)
    private String keyPrefix;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }
}
