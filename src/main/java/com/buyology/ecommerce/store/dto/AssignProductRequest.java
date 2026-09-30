package com.buyology.ecommerce.store.dto;

import com.buyology.ecommerce.product.domain.Product.DiscountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Assign a global product to a store with store-specific price and stock")
public class AssignProductRequest {

    @NotNull(message = "productId is required")
    @Schema(description = "UUID of the global product to assign")
    private UUID productId;

    @NotNull(message = "storePrice is required")
    @DecimalMin(value = "0.00", message = "storePrice must be non-negative")
    @Schema(description = "Product price in the store's local currency", example = "45000.00")
    private BigDecimal storePrice;

    @Schema(description = "Discount type — FIXED sets an absolute discounted price, PERCENTAGE applies % off",
            allowableValues = {"FIXED", "PERCENTAGE"})
    private DiscountType discountType;

    @DecimalMin(value = "0.00", message = "discountValue must be non-negative")
    @Schema(description = "Discount amount: final price when FIXED, percentage (0–100) when PERCENTAGE", example = "40000.00")
    private BigDecimal discountValue;

    @Schema(description = "When the discount starts applying (instant, UTC). Null = already started. "
            + "Together with discountEndsAt this makes the discount a FLASH SALE rather than a permanent markdown.",
            example = "2026-03-25T20:00:00Z")
    private Instant discountStartsAt;

    @Schema(description = "When the discount stops applying (instant, UTC, inclusive). Null = never ends, "
            + "i.e. an ordinary permanent markdown. For a calendar end date in the shop's own timezone use "
            + "the flash-sale endpoint, which converts \"ends 31 March\" to the end of that day in Asia/Dubai.",
            example = "2026-03-31T20:00:00Z")
    private Instant discountEndsAt;

    @Schema(description = "Whether this product is active in the store", defaultValue = "true")
    private Boolean isActive = true;

    @Schema(description = "Whether this product is available in the consumer shop (B2C channel)", defaultValue = "true")
    private Boolean b2cEnabled = true;

    @Schema(description = "Whether this product is available for B2B (quote) browse", defaultValue = "false")
    private Boolean b2bEnabled = false;

    @Valid
    @Schema(description = "Optional list of variants to assign at the same time. Can also be added later via the variants endpoint.")
    private List<AssignVariantRequest> variants;

    // Getters & Setters

    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }

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

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Boolean getB2cEnabled() { return b2cEnabled; }
    public void setB2cEnabled(Boolean b2cEnabled) { this.b2cEnabled = b2cEnabled; }

    public Boolean getB2bEnabled() { return b2bEnabled; }
    public void setB2bEnabled(Boolean b2bEnabled) { this.b2bEnabled = b2bEnabled; }

    public List<AssignVariantRequest> getVariants() { return variants; }
    public void setVariants(List<AssignVariantRequest> variants) { this.variants = variants; }
}
