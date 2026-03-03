package com.buyology.ecommerce.role.dto;

import com.buyology.ecommerce.role.domain.Role;

import java.time.Instant;
import java.util.UUID;

public class RoleResponse {

    private UUID id;
    private String name;
    private String description;
    private Boolean isSystem;
    private Instant createdAt;
    private Instant updatedAt;

    public static RoleResponse from(Role role) {
        RoleResponse response = new RoleResponse();
        response.id = role.getId();
        response.name = role.getName();
        response.description = role.getDescription();
        response.isSystem = role.getIsSystem();
        response.createdAt = role.getCreatedAt();
        response.updatedAt = role.getUpdatedAt();
        return response;
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
}
