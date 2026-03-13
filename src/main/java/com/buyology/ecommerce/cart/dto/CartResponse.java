package com.buyology.ecommerce.cart.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class CartResponse {

    private UUID id;
    private UUID authCredentialId;
    private String status;
    private BigDecimal totalPrice;
    private List<CartItemResponse> items;
    private Instant createdAt;
    private Instant updatedAt;

    public CartResponse() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getAuthCredentialId() { return authCredentialId; }
    public void setAuthCredentialId(UUID authCredentialId) { this.authCredentialId = authCredentialId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getTotalPrice() { return totalPrice; }
    public void setTotalPrice(BigDecimal totalPrice) { this.totalPrice = totalPrice; }

    public List<CartItemResponse> getItems() { return items; }
    public void setItems(List<CartItemResponse> items) { this.items = items; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
