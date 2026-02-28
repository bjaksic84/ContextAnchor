package com.ragengine.audit;

import com.ragengine.domain.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service for recording audit log entries asynchronously.
 * All audit operations are non-blocking so they don't impact request latency.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Records an audit event for the current authenticated user.
     */
    @Async
    public void logAction(String action, String resourceType, UUID resourceId, String details) {
        try {
            AuditLog.AuditLogBuilder builder = AuditLog.builder()
                    .action(action)
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .details(details);

            // Extract user context if available
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof User user) {
                builder.tenantId(user.getTenant().getId())
                        .userId(user.getId())
                        .userEmail(user.getEmail());
            }

            auditLogRepository.save(builder.build());
            log.debug("Audit log recorded: {} on {} {}", action, resourceType, resourceId);
        } catch (Exception e) {
            // Never let audit logging break the main flow
            log.error("Failed to record audit log: {}", e.getMessage());
        }
    }

    /**
     * Records an audit event with explicit tenant/user context.
     * Used for actions where SecurityContext may not be available (e.g., login).
     */
    @Async
    public void logAction(String action, UUID tenantId, UUID userId, String userEmail,
                          String resourceType, UUID resourceId, String details) {
        try {
            AuditLog entry = AuditLog.builder()
                    .action(action)
                    .tenantId(tenantId)
                    .userId(userId)
                    .userEmail(userEmail)
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .details(details)
                    .build();

            auditLogRepository.save(entry);
            log.debug("Audit log recorded: {} by {} on {} {}", action, userEmail, resourceType, resourceId);
        } catch (Exception e) {
            log.error("Failed to record audit log: {}", e.getMessage());
        }
    }

    /**
     * Records a failed action.
     */
    @Async
    public void logFailure(String action, String resourceType, UUID resourceId, String errorMessage) {
        try {
            AuditLog.AuditLogBuilder builder = AuditLog.builder()
                    .action(action)
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .success(false)
                    .errorMessage(errorMessage);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof User user) {
                builder.tenantId(user.getTenant().getId())
                        .userId(user.getId())
                        .userEmail(user.getEmail());
            }

            auditLogRepository.save(builder.build());
        } catch (Exception e) {
            log.error("Failed to record audit failure log: {}", e.getMessage());
        }
    }
}
