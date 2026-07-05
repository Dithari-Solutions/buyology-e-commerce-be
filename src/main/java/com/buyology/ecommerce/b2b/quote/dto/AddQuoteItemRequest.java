package com.buyology.ecommerce.b2b.quote.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Add a line to (or upsert an existing line on) the B2B cart. */
public class AddQuoteItemRequest {

    @NotNull
    private UUID storeProductId;

    /** Optional — a specific variant of the product. */
    private UUID variantId;

    @NotNull
    @Min(1) // >= B2B_MIN_QTY_PER_LINE is enforced in the service so the message names the actual minimum
    private Integer quantity;

    public UUID getStoreProductId() { return storeProductId; }
    public void setStoreProductId(UUID storeProductId) { this.storeProductId = storeProductId; }

    public UUID getVariantId() { return variantId; }
    public void setVariantId(UUID variantId) { this.variantId = variantId; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}
