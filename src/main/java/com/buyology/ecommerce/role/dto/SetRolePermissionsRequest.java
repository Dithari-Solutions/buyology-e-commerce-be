package com.buyology.ecommerce.role.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Replaces a role's permission set wholesale.
 *
 * <p>The permission matrix in the dashboard is edited as a grid and saved once; sending the desired
 * end state avoids the partial-application problem of firing one add/remove request per checkbox.
 * An empty list is valid and clears every permission on the role.
 */
public class SetRolePermissionsRequest {

    @NotNull(message = "permissionIds is required (send an empty list to clear all permissions)")
    private List<UUID> permissionIds;

    public List<UUID> getPermissionIds() { return permissionIds; }
    public void setPermissionIds(List<UUID> permissionIds) { this.permissionIds = permissionIds; }
}
