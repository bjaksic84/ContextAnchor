package com.ragengine.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Enhanced health check and system info controller.
 * Provides application status, uptime, version info, and dependency health.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "System", description = "System health and info endpoints")
@RequiredArgsConstructor
public class HealthController {

    private final DataSource dataSource;
    private final Optional<BuildProperties> buildProperties;

    @Value("${rag.ai.provider:openai}")
    private String aiProvider;

    private static final LocalDateTime START_TIME = LocalDateTime.now();

    @GetMapping("/health")
    @Operation(summary = "Health check",
            description = "Returns detailed health status including database connectivity and uptime.")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("service", "ContextAnchor - Enterprise RAG Platform");
        response.put("timestamp", LocalDateTime.now());
        response.put("uptime", getUptime());

        // Version info
        buildProperties.ifPresent(props -> {
            Map<String, String> version = new LinkedHashMap<>();
            version.put("name", props.getName());
            version.put("version", props.getVersion());
            version.put("artifact", props.getArtifact());
            response.put("build", version);
        });

        // Check database connectivity
        Map<String, Object> db = new LinkedHashMap<>();
        try (Connection conn = dataSource.getConnection()) {
            db.put("status", "UP");
            db.put("database", conn.getMetaData().getDatabaseProductName());
            db.put("version", conn.getMetaData().getDatabaseProductVersion());
        } catch (Exception e) {
            db.put("status", "DOWN");
            db.put("error", e.getMessage());
            response.put("status", "DEGRADED");
        }
        response.put("database", db);

        // AI provider info
        Map<String, String> ai = new LinkedHashMap<>();
        ai.put("provider", aiProvider);
        ai.put("mode", "ollama".equalsIgnoreCase(aiProvider) ? "local/private" : "cloud");
        response.put("ai", ai);

        // Java runtime
        Map<String, String> runtime = new LinkedHashMap<>();
        runtime.put("javaVersion", System.getProperty("java.version"));
        runtime.put("javaVendor", System.getProperty("java.vendor"));
        runtime.put("maxMemoryMB", String.valueOf(Runtime.getRuntime().maxMemory() / (1024 * 1024)));
        runtime.put("freeMemoryMB", String.valueOf(Runtime.getRuntime().freeMemory() / (1024 * 1024)));
        response.put("runtime", runtime);

        return ResponseEntity.ok(response);
    }

    private String getUptime() {
        java.time.Duration duration = java.time.Duration.between(START_TIME, LocalDateTime.now());
        long days = duration.toDays();
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.getSeconds() % 60;

        if (days > 0) return String.format("%dd %dh %dm %ds", days, hours, minutes, seconds);
        if (hours > 0) return String.format("%dh %dm %ds", hours, minutes, seconds);
        if (minutes > 0) return String.format("%dm %ds", minutes, seconds);
        return String.format("%ds", seconds);
    }
}
