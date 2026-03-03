package com.buyology.ecommerce.role.dto;

import com.buyology.ecommerce.role.domain.Permission;

import java.time.Instant;
import java.util.UUID;

public class PermissionResponse {

    private UUID id;
    private String code;
    private String description;
    private Instant createdAt;

    public static PermissionResponse from(Permission permission) {
        PermissionResponse response = new PermissionResponse();
        response.id = permission.getId();
        response.code = permission.getCode();
        response.description = permission.getDescription();
        response.createdAt = permission.getCreatedAt();
        return response;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
