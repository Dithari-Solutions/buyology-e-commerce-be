package com.buyology.ecommerce.role.dto;

import com.buyology.ecommerce.role.domain.UserPermission;

import java.time.Instant;
import java.util.UUID;

public class UserPermissionResponse {

    private UUID userId;
    private UUID permissionId;
    private String permissionCode;
    private String effect;
    private UUID assignedBy;
    private Instant assignedAt;

    public static UserPermissionResponse from(UserPermission userPermission) {
        UserPermissionResponse response = new UserPermissionResponse();
        response.userId = userPermission.getUser().getId();
        response.permissionId = userPermission.getPermission().getId();
        response.permissionCode = userPermission.getPermission().getCode();
        response.effect = userPermission.getEffect().name();
        response.assignedBy = userPermission.getAssignedBy() != null ? userPermission.getAssignedBy().getId() : null;
        response.assignedAt = userPermission.getAssignedAt();
        return response;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getPermissionId() {
        return permissionId;
    }

    public String getPermissionCode() {
        return permissionCode;
    }

    public String getEffect() {
        return effect;
    }

    public UUID getAssignedBy() {
        return assignedBy;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }
}
