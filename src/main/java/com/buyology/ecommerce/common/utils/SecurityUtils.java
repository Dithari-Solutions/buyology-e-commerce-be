package com.buyology.ecommerce.common.utils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * Returns the authenticated user's UUID, or null if the request is anonymous.
     */
    public static UUID currentUserIdOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof UUID uuid) {
            return uuid;
        }
        return null;
    }

    /**
     * Returns the authenticated user's UUID, or throws 401 if the request is not authenticated.
     */
    public static UUID currentUserId() {
        UUID id = currentUserIdOrNull();
        if (id == null) {
            throw new AuthenticationCredentialsNotFoundException("Authentication required");
        }
        return id;
    }

    /**
     * Asserts the authenticated user owns the resource identified by {@code ownerUserId}.
     * Admins/superadmins bypass the ownership check. Throws 403 on mismatch.
     */
    public static void requireSelfOrAdmin(UUID ownerUserId) {
        if (isAdmin()) return;
        requireSelf(ownerUserId);
    }

    /**
     * Asserts the authenticated user IS the owner of the resource. Throws 403 on mismatch.
     */
    public static void requireSelf(UUID ownerUserId) {
        UUID current = currentUserId();
        if (ownerUserId == null || !ownerUserId.equals(current)) {
            throw new AccessDeniedException("You are not allowed to access this resource");
        }
    }

    /**
     * True if the current principal has ADMIN or SUPERADMIN authority.
     */
    public static boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return false;
        return auth.getAuthorities().stream().anyMatch(a -> {
            String r = a.getAuthority();
            return "ROLE_ADMIN".equals(r) || "ROLE_SUPERADMIN".equals(r);
        });
    }

    /**
     * Resolve the client IP, honoring X-Forwarded-For if present (proxy-aware).
     */
    public static String clientIp(HttpServletRequest request) {
        if (request == null) return null;
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) return real.trim();
        return request.getRemoteAddr();
    }

    /**
     * Hex-encoded SHA-256 of the input. Used to fingerprint anonymous viewers
     * without persisting raw IPs.
     */
    public static String sha256Hex(String input) {
        if (input == null) return null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
