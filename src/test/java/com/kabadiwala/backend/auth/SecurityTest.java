package com.kabadiwala.backend.auth;

import com.kabadiwala.backend.common.UnauthorizedResourceAccessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecurityTest {

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Ownership check: resource owner is allowed")
    void testOwnerAllowed() {
        UUID userId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(userId, "user@kabadiwala.org", UserRole.COLLECTOR);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "n/a", principal.getAuthorities())
        );

        assertDoesNotThrow(() -> SecurityUtils.verifyOwnershipOrAdmin(userId));
    }

    @Test
    @DisplayName("Ownership check: admin is allowed to access any user's resource")
    void testAdminAllowed() {
        UUID adminId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        UserPrincipal adminPrincipal = new UserPrincipal(adminId, "admin@kabadiwala.org", UserRole.ADMIN);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(adminPrincipal, "n/a", adminPrincipal.getAuthorities())
        );

        assertDoesNotThrow(() -> SecurityUtils.verifyOwnershipOrAdmin(otherUserId));
    }

    @Test
    @DisplayName("Ownership check: unauthorized user is rejected with exception")
    void testUnauthorizedUserRejected() {
        UUID collector1 = UUID.randomUUID();
        UUID collector2 = UUID.randomUUID();

        UserPrincipal principal1 = new UserPrincipal(collector1, "collector1@kabadiwala.org", UserRole.COLLECTOR);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal1, "n/a", principal1.getAuthorities())
        );

        assertThrows(UnauthorizedResourceAccessException.class, () ->
                SecurityUtils.verifyOwnershipOrAdmin(collector2));
    }
}
