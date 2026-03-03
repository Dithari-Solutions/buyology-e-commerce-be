package com.buyology.ecommerce.role.dto;

import com.buyology.ecommerce.role.domain.UserPermission;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public class AssignPermissionToUserRequest {

    @NotNull(message = "User ID is required")
    private UUID userId;

    @NotNull(message = "Permission ID is required")
    private UUID permissionId;

    @NotNull(message = "Effect is required (ALLOW or DENY)")
    private UserPermission.Effect effect;

    private UUID assignedBy;

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getPermissionId() {
        return permissionId;
    }

    public void setPermissionId(UUID permissionId) {
        this.permissionId = permissionId;
    }

    public UserPermission.Effect getEffect() {
        return effect;
    }

    public void setEffect(UserPermission.Effect effect) {
        this.effect = effect;
    }

    public UUID getAssignedBy() {
        return assignedBy;
    }

    public void setAssignedBy(UUID assignedBy) {
        this.assignedBy = assignedBy;
    }
}
