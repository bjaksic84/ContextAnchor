package com.ragengine.security;

import com.ragengine.domain.entity.User;
import com.ragengine.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Authentication filter supporting both JWT Bearer tokens and API keys.
 * 
 * Auth methods (checked in order):
 * 1. X-API-Key header → validates via ApiKeyService
 * 2. Authorization: Bearer <jwt> → validates via JwtService
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final ApiKeyService apiKeyService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // Already authenticated — skip
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        // Method 1: API Key authentication
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey != null && !apiKey.isBlank()) {
            authenticateWithApiKey(apiKey, request);
            filterChain.doFilter(request, response);
            return;
        }

        // Method 2: JWT Bearer token authentication
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            authenticateWithJwt(authHeader.substring(7), request);
        }

        filterChain.doFilter(request, response);
    }

    private void authenticateWithApiKey(String rawKey, HttpServletRequest request) {
        try {
            Optional<User> userOpt = apiKeyService.validateApiKey(rawKey);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);

                request.setAttribute("tenantId", user.getTenant().getId().toString());
                request.setAttribute("userId", user.getId().toString());
                request.setAttribute("authMethod", "API_KEY");

                log.debug("Authenticated via API key for user: {}", user.getEmail());
            } else {
                log.debug("Invalid API key provided");
            }
        } catch (Exception e) {
            log.debug("API key authentication failed: {}", e.getMessage());
        }
    }

    private void authenticateWithJwt(String jwt, HttpServletRequest request) {
        try {
            String email = jwtService.extractEmail(jwt);

            if (email != null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(email);

                if (jwtService.isTokenValid(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);

                    request.setAttribute("tenantId", jwtService.extractTenantId(jwt));
                    request.setAttribute("userId", jwtService.extractUserId(jwt));
                    request.setAttribute("authMethod", "JWT");
                }
            }
        } catch (Exception e) {
            log.debug("JWT authentication failed: {}", e.getMessage());
        }
    }
}
