package com.buyology.ecommerce.common.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies the ownership/role guards that every IDOR fix relies on. */
class SecurityUtilsTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(UUID userId, String... authorities) {
        var auths = java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, auths));
    }

    @Test
    void currentUserId_throwsWhenAnonymous() {
        assertThrows(AuthenticationCredentialsNotFoundException.class, SecurityUtils::currentUserId);
        assertNull(SecurityUtils.currentUserIdOrNull());
    }

    @Test
    void currentUserId_returnsPrincipal() {
        UUID me = UUID.randomUUID();
        authenticateAs(me, "ROLE_CUSTOMER");
        assertEquals(me, SecurityUtils.currentUserId());
    }

    @Test
    void requireSelf_passesForOwnerAndBlocksOthers() {
        UUID me = UUID.randomUUID();
        authenticateAs(me, "ROLE_CUSTOMER");
        assertDoesNotThrow(() -> SecurityUtils.requireSelf(me));
        assertThrows(AccessDeniedException.class, () -> SecurityUtils.requireSelf(UUID.randomUUID()));
        assertThrows(AccessDeniedException.class, () -> SecurityUtils.requireSelf(null));
    }

    @Test
    void isAdmin_reflectsAuthorities() {
        authenticateAs(UUID.randomUUID(), "ROLE_CUSTOMER");
        assertFalse(SecurityUtils.isAdmin());
        SecurityContextHolder.clearContext();
        authenticateAs(UUID.randomUUID(), "ROLE_ADMIN");
        assertTrue(SecurityUtils.isAdmin());
        SecurityContextHolder.clearContext();
        authenticateAs(UUID.randomUUID(), "ROLE_SUPERADMIN");
        assertTrue(SecurityUtils.isAdmin());
    }

    @Test
    void requireSelfOrAdmin_letsAdminThroughForOthersData() {
        UUID admin = UUID.randomUUID();
        authenticateAs(admin, "ROLE_ADMIN");
        // Admin may access another user's resource...
        assertDoesNotThrow(() -> SecurityUtils.requireSelfOrAdmin(UUID.randomUUID()));

        SecurityContextHolder.clearContext();
        UUID customer = UUID.randomUUID();
        authenticateAs(customer, "ROLE_CUSTOMER");
        // ...but a plain customer may not.
        assertDoesNotThrow(() -> SecurityUtils.requireSelfOrAdmin(customer));
        assertThrows(AccessDeniedException.class, () -> SecurityUtils.requireSelfOrAdmin(UUID.randomUUID()));
    }

    @Test
    void sha256Hex_isStableAndDistinct() {
        assertEquals(SecurityUtils.sha256Hex("abc"), SecurityUtils.sha256Hex("abc"));
        assertNotEquals(SecurityUtils.sha256Hex("abc"), SecurityUtils.sha256Hex("abd"));
        assertEquals(64, SecurityUtils.sha256Hex("anything").length());
    }
}
