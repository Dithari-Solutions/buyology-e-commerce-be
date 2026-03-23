package com.buyology.ecommerce.favorite.dto;

import java.time.Instant;
import java.util.UUID;

public class AdminFavoriteEntryResponse {

    private UUID favoriteId;
    private UUID authCredentialId;
    private String userEmail;
    private UUID productId;
    private String productSku;
    private Instant savedAt;

    public AdminFavoriteEntryResponse() {
    }

    public AdminFavoriteEntryResponse(UUID favoriteId, UUID authCredentialId, String userEmail,
                                      UUID productId, String productSku, Instant savedAt) {
        this.favoriteId = favoriteId;
        this.authCredentialId = authCredentialId;
        this.userEmail = userEmail;
        this.productId = productId;
        this.productSku = productSku;
        this.savedAt = savedAt;
    }

    public UUID getFavoriteId() { return favoriteId; }
    public void setFavoriteId(UUID favoriteId) { this.favoriteId = favoriteId; }

    public UUID getAuthCredentialId() { return authCredentialId; }
    public void setAuthCredentialId(UUID authCredentialId) { this.authCredentialId = authCredentialId; }

    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }

    public String getProductSku() { return productSku; }
    public void setProductSku(String productSku) { this.productSku = productSku; }

    public Instant getSavedAt() { return savedAt; }
    public void setSavedAt(Instant savedAt) { this.savedAt = savedAt; }
}
