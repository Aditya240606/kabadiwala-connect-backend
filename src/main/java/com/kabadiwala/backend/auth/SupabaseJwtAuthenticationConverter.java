package com.kabadiwala.backend.auth;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Map;
import java.util.UUID;

public class SupabaseJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (Exception e) {
            userId = UUID.nameUUIDFromBytes(jwt.getSubject().getBytes());
        }

        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            email = userId + "@kabadiwala.internal";
        }
        UserRole role = extractRole(jwt);

        UserPrincipal principal = new UserPrincipal(userId, email, role);
        return new UsernamePasswordAuthenticationToken(principal, "n/a", principal.getAuthorities());
    }

    private UserRole extractRole(Jwt jwt) {
        // Check top-level role
        String roleStr = jwt.getClaimAsString("role");

        // Check user_metadata or app_metadata
        if (roleStr == null || roleStr.equalsIgnoreCase("authenticated")) {
            Map<String, Object> appMetadata = jwt.getClaim("app_metadata");
            if (appMetadata != null && appMetadata.containsKey("role")) {
                roleStr = String.valueOf(appMetadata.get("role"));
            }
        }

        if (roleStr == null || roleStr.equalsIgnoreCase("authenticated")) {
            Map<String, Object> userMetadata = jwt.getClaim("user_metadata");
            if (userMetadata != null && userMetadata.containsKey("role")) {
                roleStr = String.valueOf(userMetadata.get("role"));
            }
        }

        if (roleStr != null) {
            try {
                return UserRole.valueOf(roleStr.toUpperCase().replace("ROLE_", ""));
            } catch (IllegalArgumentException ignored) {
            }
        }

        return UserRole.COLLECTOR; // Default role for authenticated workers
    }
}
