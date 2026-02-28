package com.ragengine.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Health check and system info controller.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "System", description = "System health and info endpoints")
public class HealthController {

    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Returns the health status of the API.")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "Enterprise RAG Platform",
                "timestamp", LocalDateTime.now()
        ));
    }
}
