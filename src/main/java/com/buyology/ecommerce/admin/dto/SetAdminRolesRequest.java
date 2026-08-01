package com.buyology.ecommerce.admin.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/**
 * Replaces an admin's roles with exactly this set.
 *
 * <p>Sent as the desired end state rather than one add/remove per checkbox, so a failed save leaves
 * the admin's access untouched instead of half-applied. At least one role is required — an admin
 * with none can sign in but reaches nothing, which reads as a broken account rather than a
 * restricted one.
 */
public class SetAdminRolesRequest {

    @NotEmpty(message = "At least one role is required")
    private List<UUID> roleIds;

    public List<UUID> getRoleIds() { return roleIds; }
    public void setRoleIds(List<UUID> roleIds) { this.roleIds = roleIds; }
}
