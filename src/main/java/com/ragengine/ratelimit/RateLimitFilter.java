package com.ragengine.ratelimit;

import com.ragengine.security.SecurityContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * HTTP filter that enforces per-tenant rate limiting.
 * Runs after JWT authentication so we have tenant context.
 * 
 * Rate limit headers are added to every response:
 * - X-RateLimit-Remaining: tokens left in the current window
 * - Retry-After: seconds until the bucket refills (on 429 only)
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(2) // After JWT auth filter
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // Only rate-limit authenticated requests
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof com.ragengine.domain.entity.User)) {
            filterChain.doFilter(request, response);
            return;
        }

        com.ragengine.domain.entity.User user = (com.ragengine.domain.entity.User) auth.getPrincipal();
        UUID tenantId = user.getTenant().getId();

        // Check general API rate limit
        if (!rateLimitService.tryConsumeApiRequest(tenantId)) {
            sendRateLimitResponse(response, "API rate limit exceeded. Try again shortly.");
            return;
        }

        // Add rate limit headers
        long remaining = rateLimitService.getRemainingApiTokens(tenantId);
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        // Don't rate-limit public endpoints
        return path.startsWith("/api/v1/auth/")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/api-docs")
                || path.startsWith("/actuator")
                || path.equals("/api/v1/health");
    }

    private void sendRateLimitResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", "60");
        response.getWriter().write("""
                {
                    "status": 429,
                    "error": "Too Many Requests",
                    "message": "%s"
                }
                """.formatted(message));
    }
}
