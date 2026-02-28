package com.ragengine.controller;

import com.ragengine.domain.dto.*;
import com.ragengine.security.SecurityContext;
import com.ragengine.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for authentication operations.
 * All endpoints under /api/v1/auth are publicly accessible.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Registration, login, token refresh, and logout")
public class AuthController {

    private final AuthService authService;
    private final SecurityContext securityContext;

    @PostMapping("/register")
    @Operation(summary = "Register a new user",
            description = "Create a new user account and organization. " +
                    "The first user in an organization gets the ADMIN role.")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    @Operation(summary = "Login",
            description = "Authenticate with email and password. Returns access and refresh tokens.")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token",
            description = "Exchange a valid refresh token for a new access token. " +
                    "The old refresh token is invalidated (token rotation).")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refresh(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout",
            description = "Invalidates all refresh tokens for the current user.")
    public ResponseEntity<Map<String, String>> logout() {
        authService.logout(securityContext.getCurrentUserId());
        return ResponseEntity.ok(Map.of("message", "Successfully logged out"));
    }
}
