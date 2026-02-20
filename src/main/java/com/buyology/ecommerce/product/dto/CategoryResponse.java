package com.buyology.ecommerce.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Category response including all translations")
public class CategoryResponse {

    private UUID id;

    @Schema(description = "Parent category ID — null if this is a root-level category")
    private UUID parentId;

    private String status;
    private Instant createdAt;
    private Instant updatedAt;

    private List<TranslationDto> translations;

    // ========================
    // Nested DTO
    // ========================

    @Schema(description = "Category translation for a single language")
    public static class TranslationDto {

        private String language;
        private String name;
        private String description;
        private String slug;

        public TranslationDto() {
        }

        public TranslationDto(String language, String name, String description, String slug) {
            this.language = language;
            this.name = name;
            this.description = description;
            this.slug = slug;
        }

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }

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

        public String getSlug() {
            return slug;
        }

        public void setSlug(String slug) {
            this.slug = slug;
        }
    }

    // ========================
    // Getters & Setters
    // ========================

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getParentId() {
        return parentId;
    }

    public void setParentId(UUID parentId) {
        this.parentId = parentId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<TranslationDto> getTranslations() {
        return translations;
    }

    public void setTranslations(List<TranslationDto> translations) {
        this.translations = translations;
    }
}
