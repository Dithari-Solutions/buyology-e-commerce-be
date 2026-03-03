package com.buyology.ecommerce.role.dto;

import com.buyology.ecommerce.role.domain.UserRole;

import java.time.Instant;
import java.util.UUID;

public class UserRoleResponse {

    private UUID userId;
    private UUID roleId;
    private String roleName;
    private UUID assignedBy;
    private Instant assignedAt;

    public static UserRoleResponse from(UserRole userRole) {
        UserRoleResponse response = new UserRoleResponse();
        response.userId = userRole.getUser().getId();
        response.roleId = userRole.getRole().getId();
        response.roleName = userRole.getRole().getName();
        response.assignedBy = userRole.getAssignedBy() != null ? userRole.getAssignedBy().getId() : null;
        response.assignedAt = userRole.getAssignedAt();
        return response;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getRoleId() {
        return roleId;
    }

    public String getRoleName() {
        return roleName;
    }

    public UUID getAssignedBy() {
        return assignedBy;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }
}
