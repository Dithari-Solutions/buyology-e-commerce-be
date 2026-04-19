package com.buyology.ecommerce.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Schema(description = "All available filter options derived from the actual product catalog")
public class ProductFiltersResponse {

    private PriceRangeDto priceRange;
    private List<String> conditions;
    private List<CategoryFilterDto> categories;
    private List<BrandFilterDto> brands;
    private List<String> availabilityStatuses;
    private List<SpecFilterDto> specs;

    // ── Nested DTOs ──────────────────────────────────────────────────────────

    @Schema(description = "Min/max price across all active store listings")
    public static class PriceRangeDto {
        private BigDecimal min;
        private BigDecimal max;

        public PriceRangeDto() {}

        public PriceRangeDto(BigDecimal min, BigDecimal max) {
            this.min = min;
            this.max = max;
        }

        public BigDecimal getMin() { return min; }
        public void setMin(BigDecimal min) { this.min = min; }
        public BigDecimal getMax() { return max; }
        public void setMax(BigDecimal max) { this.max = max; }
    }

    @Schema(description = "A category available for filtering")
    public static class CategoryFilterDto {
        private UUID id;
        private String name;
        private String slug;
        private UUID parentId;

        public CategoryFilterDto() {}

        public CategoryFilterDto(UUID id, String name, String slug, UUID parentId) {
            this.id = id;
            this.name = name;
            this.slug = slug;
            this.parentId = parentId;
        }

        public UUID getId() { return id; }
        public void setId(UUID id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getSlug() { return slug; }
        public void setSlug(String slug) { this.slug = slug; }
        public UUID getParentId() { return parentId; }
        public void setParentId(UUID parentId) { this.parentId = parentId; }
    }

    @Schema(description = "A brand available for filtering")
    public static class BrandFilterDto {
        private UUID id;
        private String name;

        public BrandFilterDto() {}

        public BrandFilterDto(UUID id, String name) {
            this.id = id;
            this.name = name;
        }

        public UUID getId() { return id; }
        public void setId(UUID id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    @Schema(description = "A dynamic spec filter group with its distinct values from the catalog")
    public static class SpecFilterDto {
        private String code;
        private String label;
        private List<String> values;

        public SpecFilterDto() {}

        public SpecFilterDto(String code, String label, List<String> values) {
            this.code = code;
            this.label = label;
            this.values = values;
        }

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public List<String> getValues() { return values; }
        public void setValues(List<String> values) { this.values = values; }
    }

    // ── Getters & Setters ────────────────────────────────────────────────────

    public PriceRangeDto getPriceRange() { return priceRange; }
    public void setPriceRange(PriceRangeDto priceRange) { this.priceRange = priceRange; }

    public List<String> getConditions() { return conditions; }
    public void setConditions(List<String> conditions) { this.conditions = conditions; }

    public List<CategoryFilterDto> getCategories() { return categories; }
    public void setCategories(List<CategoryFilterDto> categories) { this.categories = categories; }

    public List<BrandFilterDto> getBrands() { return brands; }
    public void setBrands(List<BrandFilterDto> brands) { this.brands = brands; }

    public List<String> getAvailabilityStatuses() { return availabilityStatuses; }
    public void setAvailabilityStatuses(List<String> availabilityStatuses) { this.availabilityStatuses = availabilityStatuses; }

    public List<SpecFilterDto> getSpecs() { return specs; }
    public void setSpecs(List<SpecFilterDto> specs) { this.specs = specs; }
}
