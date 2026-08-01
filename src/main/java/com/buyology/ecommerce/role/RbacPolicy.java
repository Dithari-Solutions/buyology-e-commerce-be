package com.buyology.ecommerce.role;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * The bridge that lets permission-based guards be introduced without locking anyone out.
 *
 * <p>Every admin account is {@code user_type = ADMIN}, which the JWT filter turns into
 * {@code ROLE_ADMIN}. Until now the admin endpoints were guarded by {@code hasRole('ADMIN')} alone, so
 * that single fact granted the whole dashboard and a narrower role could never take anything away.
 *
 * <p>Endpoints are therefore guarded as:
 *
 * <pre>hasRole('SUPERADMIN') or hasAuthority('&lt;code&gt;') or &#64;rbacPolicy.legacyAdmin()</pre>
 *
 * <p>With {@code rbac.strict-permissions=false} (the default) the last clause keeps every existing admin
 * working exactly as before, while the permission codes become real for anyone who is not a blanket
 * ADMIN. Once roles are configured and every admin has been granted a role that covers their job, setting
 * {@code RBAC_STRICT_PERMISSIONS=true} drops the fallback and the permission matrix starts genuinely
 * restricting access. Flipping it back is instant and needs no redeploy of the guards.
 *
 * <p>Deliberately a bean method rather than a SpEL property lookup so the decision is logged once and can
 * be unit-tested.
 */
@Component("rbacPolicy")
public class RbacPolicy {

    private static final Logger log = LoggerFactory.getLogger(RbacPolicy.class);

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final boolean strictPermissions;

    public RbacPolicy(@Value("${rbac.strict-permissions:false}") boolean strictPermissions) {
        this.strictPermissions = strictPermissions;
        if (strictPermissions) {
            log.info("RBAC strict permissions ENABLED — admin access is decided by permission codes only.");
        } else {
            log.info("RBAC strict permissions disabled — ROLE_ADMIN still grants blanket admin access. "
                    + "Set RBAC_STRICT_PERMISSIONS=true once every admin holds a role covering their duties.");
        }
    }

    /** True while a blanket {@code ROLE_ADMIN} should still satisfy a permission-guarded endpoint. */
    public boolean legacyAdmin() {
        if (strictPermissions) {
            return false;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        for (GrantedAuthority authority : auth.getAuthorities()) {
            if (ROLE_ADMIN.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    /** Whether permission codes are the sole source of truth for admin access. */
    public boolean isStrictPermissions() {
        return strictPermissions;
    }
}
