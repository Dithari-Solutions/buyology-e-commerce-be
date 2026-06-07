package com.buyology.ecommerce.cart.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class CartItemResponse {

    private UUID id;
    private UUID productId;
    private String productSku;
    private UUID variantId;
    private String variantSku;
    private UUID storeId;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
    /** Pre-discount unit/total price for strike-through display. Null when not discounted. */
    private BigDecimal originalUnitPrice;
    private BigDecimal originalTotalPrice;
    /** True when the item's store is within the 30-minute delivery radius of the user's location. */
    private boolean quickDelivery;
    private List<CartItemSpecSelectionResponse> selectedSpecs;
    private Instant createdAt;
    private Instant updatedAt;

    public CartItemResponse() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }

    public String getProductSku() { return productSku; }
    public void setProductSku(String productSku) { this.productSku = productSku; }

    public UUID getVariantId() { return variantId; }
    public void setVariantId(UUID variantId) { this.variantId = variantId; }

    public String getVariantSku() { return variantSku; }
    public void setVariantSku(String variantSku) { this.variantSku = variantSku; }

    public UUID getStoreId() { return storeId; }
    public void setStoreId(UUID storeId) { this.storeId = storeId; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public BigDecimal getTotalPrice() { return totalPrice; }
    public void setTotalPrice(BigDecimal totalPrice) { this.totalPrice = totalPrice; }

    public BigDecimal getOriginalUnitPrice() { return originalUnitPrice; }
    public void setOriginalUnitPrice(BigDecimal originalUnitPrice) { this.originalUnitPrice = originalUnitPrice; }

    public BigDecimal getOriginalTotalPrice() { return originalTotalPrice; }
    public void setOriginalTotalPrice(BigDecimal originalTotalPrice) { this.originalTotalPrice = originalTotalPrice; }

    public boolean isQuickDelivery() { return quickDelivery; }
    public void setQuickDelivery(boolean quickDelivery) { this.quickDelivery = quickDelivery; }

    public List<CartItemSpecSelectionResponse> getSelectedSpecs() { return selectedSpecs; }
    public void setSelectedSpecs(List<CartItemSpecSelectionResponse> selectedSpecs) { this.selectedSpecs = selectedSpecs; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
