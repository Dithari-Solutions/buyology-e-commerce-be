package com.buyology.ecommerce.favorite.dto;

import java.util.List;
import java.util.UUID;

public class FavoriteListResponse {

    private UUID authCredentialId;
    private int total;
    private List<FavoriteItemResponse> items;

    public FavoriteListResponse() {
    }

    public FavoriteListResponse(UUID authCredentialId, List<FavoriteItemResponse> items) {
        this.authCredentialId = authCredentialId;
        this.items = items;
        this.total = items != null ? items.size() : 0;
    }

    public UUID getAuthCredentialId() { return authCredentialId; }
    public void setAuthCredentialId(UUID authCredentialId) { this.authCredentialId = authCredentialId; }

    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }

    public List<FavoriteItemResponse> getItems() { return items; }
    public void setItems(List<FavoriteItemResponse> items) { this.items = items; }
}
