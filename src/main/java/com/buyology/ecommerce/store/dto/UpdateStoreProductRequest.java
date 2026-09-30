package com.buyology.ecommerce.store.dto;

import com.buyology.ecommerce.product.domain.Product.DiscountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Update price, discount, or active status of a store product assignment")
public class UpdateStoreProductRequest {

    @DecimalMin(value = "0.00", message = "storePrice must be non-negative")
    @Schema(description = "New price in the store's local currency")
    private BigDecimal storePrice;

    @Schema(description = "Discount type — FIXED or PERCENTAGE. Null leaves it unchanged; use clearDiscount to remove.")
    private DiscountType discountType;

    @DecimalMin(value = "0.00", message = "discountValue must be non-negative")
    @Schema(description = "Discount amount: final price when FIXED, percentage (0–100) when PERCENTAGE. Null leaves it unchanged; use clearDiscount to remove.")
    private BigDecimal discountValue;

    @Schema(description = "When the discount starts applying (instant, UTC). Null leaves it unchanged; "
            + "an unset start means the discount is already live.")
    private Instant discountStartsAt;

    @Schema(description = "When the discount stops applying (instant, UTC, inclusive). Null leaves it "
            + "unchanged; an unset end means the discount never expires.")
    private Instant discountEndsAt;

    @Schema(description = "Remove the discount entirely — type, value AND the window. This exists because "
            + "null cannot mean \"clear\" on the fields above (null means \"leave alone\" on every other field "
            + "of this PATCH), so without an explicit flag there was no way to take a product off a sale.",
            defaultValue = "false")
    private Boolean clearDiscount;

    @Schema(description = "Clear just the dates, keeping the discount — i.e. turn a flash sale into a "
            + "permanent markdown.", defaultValue = "false")
    private Boolean clearDiscountWindow;

    @Schema(description = "Activate or deactivate this product in the store")
    private Boolean isActive;

    @Schema(description = "Enable/disable this product in the consumer shop (B2C channel). Null leaves it unchanged.")
    private Boolean b2cEnabled;

    @Schema(description = "Enable/disable this product for B2B (quote) browse. Null leaves it unchanged.")
    private Boolean b2bEnabled;

    // Getters & Setters

    public BigDecimal getStorePrice() { return storePrice; }
    public void setStorePrice(BigDecimal storePrice) { this.storePrice = storePrice; }

    public DiscountType getDiscountType() { return discountType; }
    public void setDiscountType(DiscountType discountType) { this.discountType = discountType; }

    public BigDecimal getDiscountValue() { return discountValue; }
    public void setDiscountValue(BigDecimal discountValue) { this.discountValue = discountValue; }

    public Instant getDiscountStartsAt() { return discountStartsAt; }
    public void setDiscountStartsAt(Instant discountStartsAt) { this.discountStartsAt = discountStartsAt; }

    public Instant getDiscountEndsAt() { return discountEndsAt; }
    public void setDiscountEndsAt(Instant discountEndsAt) { this.discountEndsAt = discountEndsAt; }

    public Boolean getClearDiscount() { return clearDiscount; }
    public void setClearDiscount(Boolean clearDiscount) { this.clearDiscount = clearDiscount; }

    public Boolean getClearDiscountWindow() { return clearDiscountWindow; }
    public void setClearDiscountWindow(Boolean clearDiscountWindow) { this.clearDiscountWindow = clearDiscountWindow; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Boolean getB2cEnabled() { return b2cEnabled; }
    public void setB2cEnabled(Boolean b2cEnabled) { this.b2cEnabled = b2cEnabled; }

    public Boolean getB2bEnabled() { return b2bEnabled; }
    public void setB2bEnabled(Boolean b2bEnabled) { this.b2bEnabled = b2bEnabled; }
}
