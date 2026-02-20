package com.buyology.ecommerce.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "Request body for creating a root category or a subcategory. "
        + "Set parentId to create a subcategory under an existing category.")
public class CreateCategoryRequest {

    @Schema(
            description = "UUID of the parent category. Leave null to create a root-level category. "
                    + "Provide a valid UUID to create a subcategory.",
            example = "3fa85f64-5717-4562-b3fc-2c963f66afa6",
            nullable = true)
    private UUID parentId;

    @Schema(description = "Category status", example = "ACTIVE", defaultValue = "ACTIVE")
    private String status = "ACTIVE";

    @NotNull(message = "Translations are required")
    @Valid
    @Schema(description = "Required translations in Azerbaijani, English, and Arabic")
    private CategoryTranslationRequest translations;

    // ========================
    // Getters & Setters
    // ========================

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

    public CategoryTranslationRequest getTranslations() {
        return translations;
    }

    public void setTranslations(CategoryTranslationRequest translations) {
        this.translations = translations;
    }
}
