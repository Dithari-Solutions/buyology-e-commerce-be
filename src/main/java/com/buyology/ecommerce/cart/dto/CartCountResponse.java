package com.buyology.ecommerce.cart.dto;

/**
 * Lightweight cart count for badge display on app/web launch without fetching the
 * full cart. {@code itemCount} = distinct lines; {@code totalQuantity} = sum of quantities.
 */
public class CartCountResponse {

    private int itemCount;
    private int totalQuantity;

    public CartCountResponse() {}

    public CartCountResponse(int itemCount, int totalQuantity) {
        this.itemCount = itemCount;
        this.totalQuantity = totalQuantity;
    }

    public int getItemCount() { return itemCount; }
    public void setItemCount(int itemCount) { this.itemCount = itemCount; }

    public int getTotalQuantity() { return totalQuantity; }
    public void setTotalQuantity(int totalQuantity) { this.totalQuantity = totalQuantity; }
}
