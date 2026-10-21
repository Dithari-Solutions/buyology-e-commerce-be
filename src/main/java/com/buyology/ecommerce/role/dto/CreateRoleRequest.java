package com.buyology.ecommerce.role.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public class CreateRoleRequest {

    @NotBlank(message = "Role name is required")
    @Size(max = 50, message = "Role name must not exceed 50 characters")
    private String name;

    private String description;

    @Size(max = 500, message = "Too many permissions")
    private List<@NotNull UUID> permissionIds = List.of();

    public List<UUID> getPermissionIds() { return permissionIds; }
    public void setPermissionIds(List<UUID> permissionIds) { this.permissionIds = permissionIds; }

    private Boolean isSystem = true;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getIsSystem() {
        return isSystem;
    }

    public void setIsSystem(Boolean isSystem) {
        this.isSystem = isSystem;
    }
}
