package com.buyology.ecommerce.store.dto;

import java.time.Instant;
import java.util.UUID;

public class StoreTranslationResponse {

    private UUID id;
    private String language;
    private String name;
    private String description;
    private Instant createdAt;
    private Instant updatedAt;

    public StoreTranslationResponse() {}

    public StoreTranslationResponse(UUID id, String language, String name,
                                    String description, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.language = language;
        this.name = name;
        this.description = description;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
