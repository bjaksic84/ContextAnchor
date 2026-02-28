package com.ragengine.audit;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Audit log entity that records significant actions in the system.
 * Captures who did what, when, on which resource, and from where.
 */
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_tenant", columnList = "tenant_id"),
        @Index(name = "idx_audit_user", columnList = "user_id"),
        @Index(name = "idx_audit_action", columnList = "action"),
        @Index(name = "idx_audit_created", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "user_email", length = 255)
    private String userEmail;

    /**
     * The action performed (e.g., DOCUMENT_UPLOAD, CHAT_QUERY, USER_LOGIN).
     */
    @Column(nullable = false, length = 100)
    private String action;

    /**
     * The type of resource affected (e.g., DOCUMENT, CONVERSATION, USER).
     */
    @Column(name = "resource_type", length = 50)
    private String resourceType;

    /**
     * The ID of the specific resource affected.
     */
    @Column(name = "resource_id")
    private UUID resourceId;

    /**
     * Additional details about the action (JSON-formatted).
     */
    @Column(columnDefinition = "TEXT")
    private String details;

    /**
     * IP address of the requester.
     */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /**
     * User agent string.
     */
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /**
     * HTTP method + path of the request.
     */
    @Column(name = "request_path", length = 500)
    private String requestPath;

    /**
     * Duration of the operation in milliseconds.
     */
    @Column(name = "duration_ms")
    private Long durationMs;

    /**
     * Whether the action succeeded.
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean success = true;

    /**
     * Error message if the action failed.
     */
    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
