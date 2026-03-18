package com.buyology.ecommerce.review.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ProductReviewVoteId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "review_id")
    private UUID reviewId;

    @Column(name = "user_id")
    private UUID userId;

    public ProductReviewVoteId() {
    }

    public ProductReviewVoteId(UUID reviewId, UUID userId) {
        this.reviewId = reviewId;
        this.userId = userId;
    }

    public UUID getReviewId() {
        return reviewId;
    }

    public void setReviewId(UUID reviewId) {
        this.reviewId = reviewId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProductReviewVoteId)) return false;
        ProductReviewVoteId that = (ProductReviewVoteId) o;
        return Objects.equals(reviewId, that.reviewId) &&
               Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reviewId, userId);
    }
}
