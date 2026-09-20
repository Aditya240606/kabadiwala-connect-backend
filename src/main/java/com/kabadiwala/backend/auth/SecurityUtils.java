package com.kabadiwala.backend.auth;

import com.kabadiwala.backend.common.UnauthorizedResourceAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

public final class SecurityUtils {

    private SecurityUtils() {}

    public static Optional<UserPrincipal> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            if (auth.getPrincipal() instanceof UserPrincipal principal) {
                return Optional.of(principal);
            }
            if (auth.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt jwt) {
                SupabaseJwtAuthenticationConverter converter = new SupabaseJwtAuthenticationConverter();
                var token = converter.convert(jwt);
                if (token.getPrincipal() instanceof UserPrincipal principal) {
                    return Optional.of(principal);
                }
            }
        }
        return Optional.empty();
    }

    public static UserPrincipal getRequiredCurrentUser() {
        return getCurrentUser()
                .orElseThrow(() -> new UnauthorizedResourceAccessException("Authentication required to perform this action"));
    }

    public static UUID getCurrentUserId() {
        return getRequiredCurrentUser().getId();
    }

    public static void verifyOwnershipOrAdmin(UUID ownerId) {
        UserPrincipal current = getRequiredCurrentUser();
        if (current.getRole() == UserRole.ADMIN) {
            return;
        }
        if (!current.getId().equals(ownerId)) {
            throw new UnauthorizedResourceAccessException("You are not authorized to modify or view this resource");
        }
    }
}
