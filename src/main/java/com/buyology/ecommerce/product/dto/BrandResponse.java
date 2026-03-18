package com.buyology.ecommerce.product.dto;

import java.util.List;
import java.util.UUID;

public class BrandResponse {

    private UUID id;
    private String status;
    private List<TranslationDto> translations;

    public BrandResponse() {
    }

    public BrandResponse(UUID id, String status, List<TranslationDto> translations) {
        this.id = id;
        this.status = status;
        this.translations = translations;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<TranslationDto> getTranslations() { return translations; }
    public void setTranslations(List<TranslationDto> translations) { this.translations = translations; }

    public static class TranslationDto {

        private String language;
        private String name;

        public TranslationDto() {
        }

        public TranslationDto(String language, String name) {
            this.language = language;
            this.name = name;
        }

        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}
