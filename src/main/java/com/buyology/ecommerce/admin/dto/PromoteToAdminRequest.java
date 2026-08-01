package com.buyology.ecommerce.admin.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/** SUPERADMIN request to convert an existing customer account into an admin with the given roles. */
public class PromoteToAdminRequest {

    @NotEmpty(message = "At least one role is required")
    private List<UUID> roleIds;

    public List<UUID> getRoleIds() { return roleIds; }
    public void setRoleIds(List<UUID> roleIds) { this.roleIds = roleIds; }
}
