package com.buyology.ecommerce.role;

import com.buyology.ecommerce.role.domain.Permission;
import com.buyology.ecommerce.role.domain.Role;
import com.buyology.ecommerce.role.domain.RolePermission;
import com.buyology.ecommerce.role.domain.RolePermissionId;
import com.buyology.ecommerce.role.repository.PermissionRepository;
import com.buyology.ecommerce.role.repository.RolePermissionRepository;
import com.buyology.ecommerce.role.repository.RoleRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Seeds predefined roles and permissions on startup (idempotent).
 *
 * Roles and their starter access:
 *   CUSTOMER_SUPPORT — review & questions module
 *   COURIER_ADMIN    — courier module (no delete)
 *   STORE_ADMIN      — store product assignment for their store
 *   SUPERADMIN       — full access to all modules
 *
 * <p>The permission lists below are <em>starting points</em>, applied when a role row is first
 * created. Afterwards the RBAC console owns them: this class will not re-grant, and never revokes.
 * SUPERADMIN alone is kept in sync with the full permission set on every boot.
 */
@Component
public class RoleDataInitializer implements ApplicationRunner {

    // ── Permission code lists per role ───────────────────────────────────────

    private static final List<String> CUSTOMER_SUPPORT_PERMISSIONS = List.of(
            PermissionConstants.REVIEW_READ,
            PermissionConstants.REVIEW_MODERATE,
            PermissionConstants.REVIEW_REPLY_CREATE,
            PermissionConstants.REVIEW_REPLY_UPDATE,
            PermissionConstants.REVIEW_REPLY_DELETE,
            PermissionConstants.REVIEW_DELETE,
            PermissionConstants.QUESTION_READ,
            PermissionConstants.QUESTION_MODERATE,
            PermissionConstants.QUESTION_ANSWER_CREATE,
            PermissionConstants.QUESTION_ANSWER_UPDATE,
            PermissionConstants.QUESTION_ANSWER_TOGGLE,
            PermissionConstants.QUESTION_ANSWER_DELETE,
            PermissionConstants.QUESTION_DELETE
    );

    private static final List<String> COURIER_ADMIN_PERMISSIONS = List.of(
            PermissionConstants.COURIER_READ,
            PermissionConstants.COURIER_CREATE,
            PermissionConstants.COURIER_UPDATE
            // courier:delete is reserved for ADMIN / SUPERADMIN
    );

    private static final List<String> STORE_ADMIN_PERMISSIONS = List.of(
            PermissionConstants.STORE_READ,
            PermissionConstants.STORE_UPDATE,
            PermissionConstants.STORE_PRODUCT_READ,
            PermissionConstants.STORE_PRODUCT_ASSIGN,
            PermissionConstants.STORE_PRODUCT_UPDATE,
            PermissionConstants.STORE_PRODUCT_REMOVE,
            PermissionConstants.STORE_ADMIN_READ
    );

    private static final List<String> SUPPLIER_PERMISSIONS = List.of(
            PermissionConstants.SUPPLIER_PRODUCT_CREATE,
            PermissionConstants.SUPPLIER_PRODUCT_READ,
            PermissionConstants.SUPPLIER_PRODUCT_UPDATE,
            PermissionConstants.SUPPLIER_ANALYTICS_READ,
            PermissionConstants.SUPPLIER_STORE_READ
    );

    private static final List<String> ALL_PERMISSIONS = List.of(
            PermissionConstants.REVIEW_READ,
            PermissionConstants.REVIEW_MODERATE,
            PermissionConstants.REVIEW_REPLY_CREATE,
            PermissionConstants.REVIEW_REPLY_UPDATE,
            PermissionConstants.REVIEW_REPLY_DELETE,
            PermissionConstants.REVIEW_DELETE,
            PermissionConstants.QUESTION_READ,
            PermissionConstants.QUESTION_MODERATE,
            PermissionConstants.QUESTION_ANSWER_CREATE,
            PermissionConstants.QUESTION_ANSWER_UPDATE,
            PermissionConstants.QUESTION_ANSWER_TOGGLE,
            PermissionConstants.QUESTION_ANSWER_DELETE,
            PermissionConstants.QUESTION_DELETE,
            PermissionConstants.COURIER_READ,
            PermissionConstants.COURIER_CREATE,
            PermissionConstants.COURIER_UPDATE,
            PermissionConstants.COURIER_DELETE,
            PermissionConstants.STORE_READ,
            PermissionConstants.STORE_UPDATE,
            PermissionConstants.STORE_PRODUCT_READ,
            PermissionConstants.STORE_PRODUCT_ASSIGN,
            PermissionConstants.STORE_PRODUCT_UPDATE,
            PermissionConstants.STORE_PRODUCT_REMOVE,
            PermissionConstants.STORE_ADMIN_READ,
            PermissionConstants.STORE_ADMIN_ASSIGN,
            PermissionConstants.STORE_ADMIN_UPDATE,
            PermissionConstants.STORE_ADMIN_REMOVE,
            PermissionConstants.SUPPLIER_APPLICATION_READ,
            PermissionConstants.SUPPLIER_APPLICATION_REVIEW,
            PermissionConstants.SUPPLIER_PRODUCT_APPROVE,
            PermissionConstants.SUPPLIER_PRODUCT_CREATE,
            PermissionConstants.SUPPLIER_PRODUCT_READ,
            PermissionConstants.SUPPLIER_PRODUCT_UPDATE,
            PermissionConstants.SUPPLIER_ANALYTICS_READ,
            PermissionConstants.SUPPLIER_STORE_READ
    );

    // ── Role definitions ─────────────────────────────────────────────────────

    private record RoleDefinition(String name, String description, List<String> permissions) {}

    private static final List<RoleDefinition> ROLE_DEFINITIONS = List.of(
            new RoleDefinition(
                    "CUSTOMER_SUPPORT",
                    "Moderates customer reviews and product questions",
                    CUSTOMER_SUPPORT_PERMISSIONS),
            new RoleDefinition(
                    "COURIER_ADMIN",
                    "Manages courier accounts and delivery operations",
                    COURIER_ADMIN_PERMISSIONS),
            new RoleDefinition(
                    "STORE_ADMIN",
                    "Manages product assignments and inventory for their store",
                    STORE_ADMIN_PERMISSIONS),
            new RoleDefinition(
                    "MARKETING",
                    "Manages marketing: promo codes, newsletters, banners, stories and games",
                    List.of()),
            new RoleDefinition(
                    "SUPPLIER",
                    "Third-party supplier: manage own products and view own analytics",
                    SUPPLIER_PERMISSIONS),
            new RoleDefinition(
                    "PROCUREMENT",
                    "Prices B2B quote requests (RFQ) from the procurement dashboard",
                    List.of()),
            new RoleDefinition(
                    "SUPERADMIN",
                    "Full access to all modules and operations",
                    ALL_PERMISSIONS)
    );

    // ── Repositories ─────────────────────────────────────────────────────────

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;

    public RoleDataInitializer(RoleRepository roleRepository,
                               PermissionRepository permissionRepository,
                               RolePermissionRepository rolePermissionRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.rolePermissionRepository = rolePermissionRepository;
    }

    // ── Startup logic ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Ensure every permission code exists in the permissions table
        Map<String, Permission> permByCode = ensurePermissions();

        for (RoleDefinition def : ROLE_DEFINITIONS) {
            boolean existed = roleRepository.findByName(def.name()).isPresent();
            Role role = ensureRole(def.name(), def.description());

            // Grant the starter permission set only when the role is first created. Re-applying it on
            // every boot would resurrect permissions a superadmin had deliberately revoked in the RBAC
            // console — from their side, the change would simply undo itself after the next deploy.
            if (!existed) {
                grantPermissions(role, def.permissions(), permByCode);
            }
        }

        // SUPERADMIN is the exception: it is the recovery path for every other permission mistake, so
        // it stays synced to the full set and is locked against editing in the API.
        roleRepository.findByName("SUPERADMIN")
                .ifPresent(superadmin -> grantPermissions(superadmin, ALL_PERMISSIONS, permByCode));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, Permission> ensurePermissions() {
        return ALL_PERMISSIONS.stream()
                .map(code -> permissionRepository.findByCode(code).orElseGet(() -> {
                    Permission p = new Permission();
                    p.setCode(code);
                    p.setDescription(humanise(code));
                    return permissionRepository.save(p);
                }))
                .collect(Collectors.toMap(Permission::getCode, Function.identity()));
    }

    private Role ensureRole(String name, String description) {
        return roleRepository.findByName(name).orElseGet(() -> {
            Role r = new Role();
            r.setName(name);
            r.setDescription(description);
            r.setIsSystem(true);
            return roleRepository.save(r);
        });
    }

    /** Adds any missing grants. Never revokes — existing grants are the superadmin's to manage. */
    private void grantPermissions(Role role, List<String> codes, Map<String, Permission> permByCode) {
        for (String code : codes) {
            Permission permission = permByCode.get(code);
            if (permission == null) continue;
            if (!rolePermissionRepository.existsByIdRoleIdAndIdPermissionId(role.getId(), permission.getId())) {
                RolePermission rp = new RolePermission();
                rp.setId(new RolePermissionId(role.getId(), permission.getId()));
                rp.setRole(role);
                rp.setPermission(permission);
                rolePermissionRepository.save(rp);
            }
        }
    }

    /** Converts "courier:reply:create" → "Courier reply create" */
    private String humanise(String code) {
        return Arrays.stream(code.split(":"))
                .reduce((a, b) -> a + " " + b)
                .map(s -> Character.toUpperCase(s.charAt(0)) + s.substring(1))
                .orElse(code);
    }
}
