package com.buyology.ecommerce.review.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;
import java.util.UUID;

/**
 * Combined post-delivery rating: 5-star for the courier plus optional
 * product ratings for each order item.
 */
public class PostDeliveryRatingRequest {

    /** 5-star rating for the courier (1–5). Required. */
    @NotNull(message = "Courier stars rating is required")
    @Min(value = 1, message = "Courier rating must be at least 1")
    @Max(value = 5, message = "Courier rating must be at most 5")
    private Integer courierStars;

    /** Optional free-text comment about the courier. */
    @Size(max = 1000)
    private String courierComment;

    /** Optional per-product ratings. Can be empty or omitted. */
    @Valid
    private List<ProductRatingEntry> productRatings;

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public Integer getCourierStars() { return courierStars; }
    public void setCourierStars(Integer courierStars) { this.courierStars = courierStars; }

    public String getCourierComment() { return courierComment; }
    public void setCourierComment(String courierComment) { this.courierComment = courierComment; }

    public List<ProductRatingEntry> getProductRatings() { return productRatings; }
    public void setProductRatings(List<ProductRatingEntry> productRatings) {
        this.productRatings = productRatings;
    }

    /** One entry per order item the customer wants to rate. */
    public static class ProductRatingEntry {

        @NotNull(message = "productId is required for each product rating")
        private UUID productId;

        @NotNull(message = "stars is required for each product rating")
        @Min(1) @Max(5)
        private Short stars;

        @Size(max = 2000)
        private String body;

        public UUID getProductId() { return productId; }
        public void setProductId(UUID productId) { this.productId = productId; }

        public Short getStars() { return stars; }
        public void setStars(Short stars) { this.stars = stars; }

        public String getBody() { return body; }
        public void setBody(String body) { this.body = body; }
    }
}
