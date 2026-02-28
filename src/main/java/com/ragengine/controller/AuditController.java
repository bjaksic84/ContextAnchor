package com.ragengine.controller;

import com.ragengine.audit.AuditLog;
import com.ragengine.audit.AuditLogRepository;
import com.ragengine.security.SecurityContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for querying audit logs.
 * Only accessible by authenticated users within their own tenant.
 */
@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@Tag(name = "Audit", description = "Audit log query endpoints")
public class AuditController {

    private final AuditLogRepository auditLogRepository;
    private final SecurityContext securityContext;

    @GetMapping
    @Operation(summary = "List audit logs",
            description = "Returns paginated audit logs for the current tenant. " +
                    "Optionally filter by action type.")
    public ResponseEntity<Page<AuditLog>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String action) {

        UUID tenantId = securityContext.getCurrentTenantId();
        Pageable pageable = PageRequest.of(page, size);

        Page<AuditLog> logs;
        if (action != null && !action.isBlank()) {
            logs = auditLogRepository.findByTenantIdAndActionOrderByCreatedAtDesc(
                    tenantId, action, pageable);
        } else {
            logs = auditLogRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable);
        }

        return ResponseEntity.ok(logs);
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "List audit logs by user",
            description = "Returns paginated audit logs for a specific user in the current tenant.")
    public ResponseEntity<Page<AuditLog>> getAuditLogsByUser(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        UUID tenantId = securityContext.getCurrentTenantId();
        Pageable pageable = PageRequest.of(page, size);

        Page<AuditLog> logs = auditLogRepository.findByTenantIdAndUserIdOrderByCreatedAtDesc(
                tenantId, userId, pageable);

        return ResponseEntity.ok(logs);
    }
}
