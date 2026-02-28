package com.ragengine.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * HTTP request/response logging filter.
 * Logs every incoming request with method, path, timing, and response status.
 *
 * Each request is tagged with a unique correlation ID (X-Request-Id) for tracing.
 * This runs as the first filter in the chain so timing captures the full request lifecycle.
 */
@Component
@Slf4j
@Order(1) // Run first, before auth and rate limiting
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // Generate or use existing correlation ID
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString().substring(0, 8);
        }
        response.setHeader("X-Request-Id", requestId);

        String method = request.getMethod();
        String path = request.getRequestURI();
        String queryString = request.getQueryString();
        String clientIp = getClientIp(request);

        long startTime = System.currentTimeMillis();

        log.info("[{}] --> {} {} {} from {}",
                requestId, method, path,
                queryString != null ? "?" + queryString : "",
                clientIp);

        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            int status = response.getStatus();

            if (status >= 500) {
                log.error("[{}] <-- {} {} {} - {} ({}ms)",
                        requestId, method, path, status, getStatusText(status), duration);
            } else if (status >= 400) {
                log.warn("[{}] <-- {} {} {} - {} ({}ms)",
                        requestId, method, path, status, getStatusText(status), duration);
            } else {
                log.info("[{}] <-- {} {} {} - {} ({}ms)",
                        requestId, method, path, status, getStatusText(status), duration);
            }
        }
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        // Don't log actuator and static resource requests to reduce noise
        return path.startsWith("/actuator/")
                || path.startsWith("/swagger-ui/")
                || path.equals("/favicon.ico");
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String getStatusText(int status) {
        return switch (status) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 204 -> "No Content";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 409 -> "Conflict";
            case 413 -> "Payload Too Large";
            case 429 -> "Too Many Requests";
            case 500 -> "Internal Server Error";
            default -> String.valueOf(status);
        };
    }
}
