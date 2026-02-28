package com.ragengine.controller;

import com.ragengine.service.ApiKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for API key management.
 * API keys provide an alternative to JWT for programmatic/CI access.
 */
@RestController
@RequestMapping("/api/v1/api-keys")
@RequiredArgsConstructor
@Tag(name = "API Keys", description = "API key management endpoints")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @PostMapping
    @Operation(summary = "Create API key",
            description = "Creates a new API key. The raw key is only shown in this response — store it securely.")
    public ResponseEntity<Map<String, Object>> createApiKey(
            @RequestParam @NotBlank String name,
            @RequestParam(required = false) LocalDateTime expiresAt) {

        Map<String, Object> response = apiKeyService.createApiKey(name, expiresAt);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "List API keys",
            description = "Lists all active API keys for the current tenant (without raw key values).")
    public ResponseEntity<List<Map<String, Object>>> listApiKeys() {
        return ResponseEntity.ok(apiKeyService.listApiKeys());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Revoke API key",
            description = "Revokes an API key. It can no longer be used for authentication.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeApiKey(@PathVariable UUID id) {
        apiKeyService.revokeApiKey(id);
    }
}
