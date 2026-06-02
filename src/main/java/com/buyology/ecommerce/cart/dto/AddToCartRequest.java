package com.buyology.ecommerce.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public class AddToCartRequest {

    /** The store from which this product is being purchased — determines price and currency */
    @NotNull
    private UUID storeId;

    @NotNull
    private UUID productId;

    /** Optional — set for products with pre-built variants (e.g. refurbished grades) */
    private UUID variantId;

    /** Optional — set for DIY/configurable products to capture selected spec options */
    private List<UUID> specOptionIds;

    @NotNull
    @Min(1)
    @Max(1000)
    private Integer quantity = 1;

    public AddToCartRequest() {
    }

    public UUID getStoreId() { return storeId; }
    public void setStoreId(UUID storeId) { this.storeId = storeId; }

    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }

    public UUID getVariantId() { return variantId; }
    public void setVariantId(UUID variantId) { this.variantId = variantId; }

    public List<UUID> getSpecOptionIds() { return specOptionIds; }
    public void setSpecOptionIds(List<UUID> specOptionIds) { this.specOptionIds = specOptionIds; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}
