package com.buyology.ecommerce.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Filter parameters for product search — all fields are optional")
public class ProductFilterRequest {

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

    // Spec-based filters — matched against product_spec_groups.code + product_spec_options.value
    @Schema(description = "RAM size, e.g. 16GB", example = "16GB")
    private String ram;

    @Schema(description = "Storage size, e.g. 512GB SSD", example = "512GB SSD")
    private String storage;

    @Schema(description = "Processor, e.g. Intel Core i7-1255U", example = "Intel Core i7-1255U")
    private String processor;

    @Schema(description = "Screen size in inches, e.g. 15.6", example = "15.6")
    private String screenSize;

    @Schema(description = "Touchable screen: Yes or No", allowableValues = {"Yes", "No"})
    private String touchableScreen;

    @Schema(description = "Operating system", allowableValues = {"FreeDOS", "MacOS", "Win11 Home", "Win11 Pro", "Ubuntu", "No OS", "Chrome OS"})
    private String operatingSystem;

    @Schema(description = "Keyboard language, e.g. EN, RU, AZ", example = "EN")
    private String keyboardLanguage;

    @Schema(description = "Minimum price (inclusive), in the country's native currency", example = "200")
    private BigDecimal minPrice;

    @Schema(description = "Maximum price (inclusive), in the country's native currency", example = "2000")
    private BigDecimal maxPrice;

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

    public String getRam() { return ram; }
    public void setRam(String ram) { this.ram = ram; }

    public String getStorage() { return storage; }
    public void setStorage(String storage) { this.storage = storage; }

    public String getProcessor() { return processor; }
    public void setProcessor(String processor) { this.processor = processor; }

    public String getScreenSize() { return screenSize; }
    public void setScreenSize(String screenSize) { this.screenSize = screenSize; }

    public String getTouchableScreen() { return touchableScreen; }
    public void setTouchableScreen(String touchableScreen) { this.touchableScreen = touchableScreen; }

    public String getOperatingSystem() { return operatingSystem; }
    public void setOperatingSystem(String operatingSystem) { this.operatingSystem = operatingSystem; }

    public String getKeyboardLanguage() { return keyboardLanguage; }
    public void setKeyboardLanguage(String keyboardLanguage) { this.keyboardLanguage = keyboardLanguage; }

    public BigDecimal getMinPrice() { return minPrice; }
    public void setMinPrice(BigDecimal minPrice) { this.minPrice = minPrice; }

    public BigDecimal getMaxPrice() { return maxPrice; }
    public void setMaxPrice(BigDecimal maxPrice) { this.maxPrice = maxPrice; }
}
