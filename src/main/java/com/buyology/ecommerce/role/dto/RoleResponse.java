package com.buyology.ecommerce.role.dto;

import com.buyology.ecommerce.role.domain.Role;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class RoleResponse {

    private UUID id;
    private String name;
    private String description;
    private Boolean isSystem;
    private Instant createdAt;
    private Instant updatedAt;

    /** Permission codes granted by this role. */
    private List<String> permissionCodes = List.of();

    /** How many users hold this role. */
    private long userCount;

    /**
     * True for roles the platform depends on and whose name/permissions must not be edited.
     *
     * <p>SUPERADMIN is the recovery path for every other permission mistake — letting it be renamed,
     * deleted, or stripped of permissions from the UI would be unrecoverable without DB access.
     */
    private boolean locked;

    public static RoleResponse from(Role role) {
        RoleResponse response = new RoleResponse();
        response.id = role.getId();
        response.name = role.getName();
        response.description = role.getDescription();
        response.isSystem = role.getIsSystem();
        response.createdAt = role.getCreatedAt();
        response.updatedAt = role.getUpdatedAt();
        response.locked = isProtected(role.getName());
        return response;
    }

    public static RoleResponse from(Role role, List<String> permissionCodes, long userCount) {
        RoleResponse response = from(role);
        response.permissionCodes = permissionCodes == null ? List.of() : permissionCodes;
        response.userCount = userCount;
        return response;
    }

    /** Roles that may not be renamed, deleted, or have their permissions edited. */
    public static boolean isProtected(String roleName) {
        return "SUPERADMIN".equalsIgnoreCase(roleName);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Boolean getIsSystem() {
        return isSystem;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<String> getPermissionCodes() {
        return permissionCodes;
    }

    public long getUserCount() {
        return userCount;
    }

    public boolean isLocked() {
        return locked;
    }
}
