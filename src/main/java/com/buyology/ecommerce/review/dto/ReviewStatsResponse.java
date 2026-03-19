package com.buyology.ecommerce.review.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class ReviewStatsResponse {

    private UUID productId;
    private Integer totalReviews;
    private BigDecimal averageRating;
    private Integer rating1Count;
    private Integer rating2Count;
    private Integer rating3Count;
    private Integer rating4Count;
    private Integer rating5Count;
    private Instant updatedAt;

    // =====================
    // Getters & Setters
    // =====================

    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }

    public Integer getTotalReviews() { return totalReviews; }
    public void setTotalReviews(Integer totalReviews) { this.totalReviews = totalReviews; }

    public BigDecimal getAverageRating() { return averageRating; }
    public void setAverageRating(BigDecimal averageRating) { this.averageRating = averageRating; }

    public Integer getRating1Count() { return rating1Count; }
    public void setRating1Count(Integer rating1Count) { this.rating1Count = rating1Count; }

    public Integer getRating2Count() { return rating2Count; }
    public void setRating2Count(Integer rating2Count) { this.rating2Count = rating2Count; }

    public Integer getRating3Count() { return rating3Count; }
    public void setRating3Count(Integer rating3Count) { this.rating3Count = rating3Count; }

    public Integer getRating4Count() { return rating4Count; }
    public void setRating4Count(Integer rating4Count) { this.rating4Count = rating4Count; }

    public Integer getRating5Count() { return rating5Count; }
    public void setRating5Count(Integer rating5Count) { this.rating5Count = rating5Count; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
