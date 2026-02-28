package com.ragengine.service;

import com.ragengine.domain.dto.AuthResponse;
import com.ragengine.domain.dto.LoginRequest;
import com.ragengine.domain.dto.RefreshTokenRequest;
import com.ragengine.domain.dto.RegisterRequest;
import com.ragengine.domain.entity.*;
import com.ragengine.audit.AuditAction;
import com.ragengine.audit.AuditService;
import com.ragengine.repository.RefreshTokenRepository;
import com.ragengine.repository.TenantRepository;
import com.ragengine.repository.UserRepository;
import com.ragengine.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Authentication service handling registration, login, and token refresh.
 * 
 * Registration flow:
 * 1. Create tenant (organization) if it doesn't exist
 * 2. Create user with hashed password
 * 3. First user in a tenant gets ADMIN role
 * 4. Generate access + refresh tokens
 * 
 * Login flow:
 * 1. Authenticate credentials via Spring Security AuthenticationManager
 * 2. Generate new access + refresh tokens
 * 3. Invalidate old refresh tokens for that user
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final AuditService auditService;

    /**
     * Registers a new user and creates their tenant (organization).
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Check if email is taken
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email is already registered");
        }

        // Create or get tenant
        String slug = generateSlug(request.organizationName());
        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseGet(() -> {
                    Tenant newTenant = Tenant.builder()
                            .name(request.organizationName())
                            .slug(slug)
                            .build();
                    return tenantRepository.save(newTenant);
                });

        // Determine role: first user in tenant gets ADMIN
        boolean isFirstUser = userRepository.findByTenantId(tenant.getId()).isEmpty();

        // Create user
        User user = User.builder()
                .tenant(tenant)
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .role(isFirstUser ? UserRole.ADMIN : UserRole.USER)
                .build();

        user = userRepository.save(user);
        log.info("User registered: {} (tenant: {}, role: {})",
                user.getEmail(), tenant.getName(), user.getRole());

        auditService.logAction(AuditAction.USER_REGISTER,
                tenant.getId(), user.getId(), user.getEmail(),
                "USER", user.getId(),
                "Registered with role " + user.getRole());

        return generateAuthResponse(user);
    }

    /**
     * Authenticates a user and returns new tokens.
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );
        } catch (BadCredentialsException e) {
            auditService.logAction(AuditAction.USER_LOGIN_FAILED,
                    null, null, request.email(),
                    "USER", null,
                    "Failed login attempt for " + request.email());
            throw new BadCredentialsException("Invalid email or password");
        }

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!user.getActive()) {
            throw new IllegalStateException("Account is deactivated");
        }

        // Invalidate old refresh tokens
        refreshTokenRepository.deleteByUserId(user.getId());

        log.info("User logged in: {}", user.getEmail());

        auditService.logAction(AuditAction.USER_LOGIN,
                user.getTenant().getId(), user.getId(), user.getEmail(),
                "USER", user.getId(), null);

        return generateAuthResponse(user);
    }

    /**
     * Refreshes an access token using a valid refresh token.
     */
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(request.refreshToken())
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        if (refreshToken.isExpired()) {
            refreshTokenRepository.delete(refreshToken);
            throw new IllegalArgumentException("Refresh token has expired. Please login again.");
        }

        User user = refreshToken.getUser();

        // Rotate: delete old refresh token, create new one
        refreshTokenRepository.delete(refreshToken);

        log.info("Token refreshed for user: {}", user.getEmail());

        auditService.logAction(AuditAction.TOKEN_REFRESH,
                user.getTenant().getId(), user.getId(), user.getEmail(),
                "USER", user.getId(), null);

        return generateAuthResponse(user);
    }

    /**
     * Logs out by invalidating all refresh tokens for the user.
     */
    @Transactional
    public void logout(UUID userId) {
        refreshTokenRepository.deleteByUserId(userId);
        log.info("User logged out: {}", userId);
    }

    // ============================
    // Private helpers
    // ============================

    private AuthResponse generateAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getTenant().getId(),
                user.getRole().name()
        );

        String refreshTokenStr = jwtService.generateRefreshToken();

        // Save refresh token to DB
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(refreshTokenStr)
                .expiresAt(LocalDateTime.now().plusSeconds(
                        jwtService.getRefreshTokenExpiration() / 1000))
                .build();
        refreshTokenRepository.save(refreshToken);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenStr)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpiration() / 1000)
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .fullName(user.getFullName())
                        .role(user.getRole().name())
                        .tenantId(user.getTenant().getId())
                        .tenantName(user.getTenant().getName())
                        .build())
                .build();
    }

    private String generateSlug(String name) {
        return name.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }
}
