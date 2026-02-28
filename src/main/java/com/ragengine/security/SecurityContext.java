package com.ragengine.security;

import com.ragengine.domain.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Utility class to access the current authenticated user's context.
 * Provides convenient methods to get userId, tenantId, and user entity
 * from the SecurityContext without boilerplate in every service.
 */
@Component
public class SecurityContext {

    /**
     * Gets the currently authenticated user entity.
     *
     * @return the User entity
     * @throws IllegalStateException if no user is authenticated
     */
    public User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User)) {
            throw new IllegalStateException("No authenticated user found");
        }
        return (User) auth.getPrincipal();
    }

    /**
     * Gets the current user's ID.
     */
    public UUID getCurrentUserId() {
        return getCurrentUser().getId();
    }

    /**
     * Gets the current user's tenant ID.
     */
    public UUID getCurrentTenantId() {
        return getCurrentUser().getTenant().getId();
    }

    /**
     * Checks if the current user has the ADMIN role.
     */
    public boolean isAdmin() {
        return getCurrentUser().getRole().name().equals("ADMIN");
    }
}
