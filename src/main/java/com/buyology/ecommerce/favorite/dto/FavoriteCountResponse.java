package com.buyology.ecommerce.favorite.dto;

/**
 * Lightweight favourites count for badge display on app/web launch.
 */
public class FavoriteCountResponse {

    private long count;

    public FavoriteCountResponse() {}

    public FavoriteCountResponse(long count) {
        this.count = count;
    }

    public long getCount() { return count; }
    public void setCount(long count) { this.count = count; }
}
