package com.buyology.ecommerce.product.dto;

import com.buyology.ecommerce.common.enums.SpecUnit;

import java.util.List;
import java.util.UUID;

public class GlobalSpecGroupResponse {

    private UUID id;
    private String code;
    private List<TranslationDto> translations;
    private List<OptionDto> options;

    public GlobalSpecGroupResponse() {
    }

    public GlobalSpecGroupResponse(UUID id, String code, List<TranslationDto> translations, List<OptionDto> options) {
        this.id = id;
        this.code = code;
        this.translations = translations;
        this.options = options;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public List<TranslationDto> getTranslations() { return translations; }
    public void setTranslations(List<TranslationDto> translations) { this.translations = translations; }

    public List<OptionDto> getOptions() { return options; }
    public void setOptions(List<OptionDto> options) { this.options = options; }

    // ─── Nested DTOs ────────────────────────────────────────────────────────

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

    public static class OptionDto {

        private UUID id;
        private SpecUnit unit;
        private List<OptionTranslationDto> translations;

        public OptionDto() {
        }

        public OptionDto(UUID id, SpecUnit unit, List<OptionTranslationDto> translations) {
            this.id = id;
            this.unit = unit;
            this.translations = translations;
        }

        public UUID getId() { return id; }
        public void setId(UUID id) { this.id = id; }

        public SpecUnit getUnit() { return unit; }
        public void setUnit(SpecUnit unit) { this.unit = unit; }

        public List<OptionTranslationDto> getTranslations() { return translations; }
        public void setTranslations(List<OptionTranslationDto> translations) { this.translations = translations; }
    }

    public static class OptionTranslationDto {

        private String language;
        private String value;

        public OptionTranslationDto() {
        }

        public OptionTranslationDto(String language, String value) {
            this.language = language;
            this.value = value;
        }

        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }

        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }
}
