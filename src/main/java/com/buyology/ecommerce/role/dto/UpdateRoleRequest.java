package com.buyology.ecommerce.role.dto;

import jakarta.validation.constraints.Size;

public class UpdateRoleRequest {

    @Size(max = 50, message = "Role name must not exceed 50 characters")
    private String name;

    private String description;

    private Boolean isSystem;

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
