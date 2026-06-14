package com.buyology.ecommerce.product.dto;

import java.time.Instant;
import java.util.UUID;

public class CategoryLocalizedResponse {

    private UUID id;
    private UUID parentId;
    private String status;
    private String icon;
    private String name;
    private String description;
    private String slug;
    private Instant createdAt;
    private Instant updatedAt;

    public CategoryLocalizedResponse() {
    }

    public CategoryLocalizedResponse(UUID id, UUID parentId, String status,
            String name, String description, String slug,
            Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.parentId = parentId;
        this.status = status;
        this.name = name;
        this.description = description;
        this.slug = slug;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getParentId() { return parentId; }
    public void setParentId(UUID parentId) { this.parentId = parentId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
