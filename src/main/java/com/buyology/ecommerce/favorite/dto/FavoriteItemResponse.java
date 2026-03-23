package com.buyology.ecommerce.favorite.dto;

import java.time.Instant;
import java.util.UUID;

public class FavoriteItemResponse {

    private UUID id;
    private UUID productId;
    private String productSku;
    private Instant savedAt;

    public FavoriteItemResponse() {
    }

    public FavoriteItemResponse(UUID id, UUID productId, String productSku, Instant savedAt) {
        this.id = id;
        this.productId = productId;
        this.productSku = productSku;
        this.savedAt = savedAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }

    public String getProductSku() { return productSku; }
    public void setProductSku(String productSku) { this.productSku = productSku; }

    public Instant getSavedAt() { return savedAt; }
    public void setSavedAt(Instant savedAt) { this.savedAt = savedAt; }
}
