package com.buyology.ecommerce.store.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class StoreTranslationRequest {

    @NotBlank(message = "Language is required (e.g. en, az, ar)")
    @Size(max = 5)
    private String language;

    @NotBlank(message = "Name is required")
    @Size(max = 255)
    private String name;

    private String description;

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
