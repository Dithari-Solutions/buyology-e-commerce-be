package com.buyology.ecommerce.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Schema(description = "Filter parameters for product search — all fields are optional")
public class ProductFilterRequest {

    @Schema(description = "General search query (matches product title in any language)", example = "Gaming Laptop")
    private String q;

    @Schema(description = "Condition: NEW or REFURBISHED", allowableValues = {"NEW", "REFURBISHED"})
    private String condition;

    @Schema(description = "Brand ID to filter by")
    private UUID brandId;

    @Schema(description = "Availability status", allowableValues = {"IN_STOCK", "OUT_OF_STOCK", "PRE_ORDER"})
    private String availabilityStatus;

    @Schema(description = "Category ID to filter by")
    private UUID categoryId;

    @Schema(description = "Only super deals", example = "true")
    private Boolean isSuperDeal;

    @Schema(description = "Only limited stock products", example = "true")
    private Boolean isLimitedStock;

    // Spec-based filters — matched against product_spec_groups.code + product_spec_options.value.
    // Multi-select: a product matches when its value is ANY of the supplied values (OR within a
    // spec code), and ALL supplied spec codes must match (AND across codes). Bind via repeated
    // query params, e.g. ?ram=8&ram=16.
    @Schema(description = "RAM values, e.g. 8, 16", example = "16")
    private List<String> ram;

    @Schema(description = "Storage values, e.g. 256, 512", example = "512")
    private List<String> storage;

    @Schema(description = "Processor values", example = "Intel Core i7-1255U")
    private List<String> processor;

    @Schema(description = "Screen size values in inches", example = "15.6")
    private List<String> screenSize;

    @Schema(description = "Touchable screen: Yes or No", allowableValues = {"Yes", "No"})
    private List<String> touchableScreen;

    @Schema(description = "Operating system values")
    private List<String> operatingSystem;

    @Schema(description = "Keyboard language values, e.g. EN, RU, AZ", example = "EN")
    private List<String> keyboardLanguage;

    @Schema(description = "Minimum price (inclusive), in the country's native currency", example = "200")
    private BigDecimal minPrice;

    @Schema(description = "Maximum price (inclusive), in the country's native currency", example = "2000")
    private BigDecimal maxPrice;

    @Schema(description = "Sort order", allowableValues = {"POPULAR", "NEWEST", "PRICE_ASC", "PRICE_DESC"})
    private String sort;

    public String getQ() { return q; }
    public void setQ(String q) { this.q = q; }

    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }

    public UUID getBrandId() { return brandId; }
    public void setBrandId(UUID brandId) { this.brandId = brandId; }

    public String getAvailabilityStatus() { return availabilityStatus; }
    public void setAvailabilityStatus(String availabilityStatus) { this.availabilityStatus = availabilityStatus; }

    public UUID getCategoryId() { return categoryId; }
    public void setCategoryId(UUID categoryId) { this.categoryId = categoryId; }

    public Boolean getIsSuperDeal() { return isSuperDeal; }
    public void setIsSuperDeal(Boolean isSuperDeal) { this.isSuperDeal = isSuperDeal; }

    public Boolean getIsLimitedStock() { return isLimitedStock; }
    public void setIsLimitedStock(Boolean isLimitedStock) { this.isLimitedStock = isLimitedStock; }

    public List<String> getRam() { return ram; }
    public void setRam(List<String> ram) { this.ram = ram; }

    public List<String> getStorage() { return storage; }
    public void setStorage(List<String> storage) { this.storage = storage; }

    public List<String> getProcessor() { return processor; }
    public void setProcessor(List<String> processor) { this.processor = processor; }

    public List<String> getScreenSize() { return screenSize; }
    public void setScreenSize(List<String> screenSize) { this.screenSize = screenSize; }

    public List<String> getTouchableScreen() { return touchableScreen; }
    public void setTouchableScreen(List<String> touchableScreen) { this.touchableScreen = touchableScreen; }

    public List<String> getOperatingSystem() { return operatingSystem; }
    public void setOperatingSystem(List<String> operatingSystem) { this.operatingSystem = operatingSystem; }

    public List<String> getKeyboardLanguage() { return keyboardLanguage; }
    public void setKeyboardLanguage(List<String> keyboardLanguage) { this.keyboardLanguage = keyboardLanguage; }

    public BigDecimal getMinPrice() { return minPrice; }
    public void setMinPrice(BigDecimal minPrice) { this.minPrice = minPrice; }

    public BigDecimal getMaxPrice() { return maxPrice; }
    public void setMaxPrice(BigDecimal maxPrice) { this.maxPrice = maxPrice; }

    public String getSort() { return sort; }
    public void setSort(String sort) { this.sort = sort; }
}
