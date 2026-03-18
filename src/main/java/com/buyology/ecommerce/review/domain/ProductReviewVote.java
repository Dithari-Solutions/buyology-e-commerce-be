package com.buyology.ecommerce.review.domain;

import com.buyology.ecommerce.user.domain.Users;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * Helpful / not-helpful vote on a review.
 *
 * Composite PK (review_id + user_id) prevents double-voting at the DB level.
 * The PK IS the uniqueness index — no redundant separate constraint needed.
 *
 * @MapsId must reference the Java field name inside the @Embeddable, not the column name.
 * insertable=false + updatable=false on both @JoinColumn declarations is mandatory
 * to avoid Hibernate "repeated column in mapping" startup error.
 */
@Entity
@Table(name = "product_review_votes")
public class ProductReviewVote {

    @EmbeddedId
    private ProductReviewVoteId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("reviewId")
    @JoinColumn(name = "review_id", insertable = false, updatable = false)
    private ProductReview review;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("userId")
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private Users user;

    @Column(name = "is_helpful", nullable = false)
    private Boolean isHelpful;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // =====================
    // Constructors
    // =====================

    public ProductReviewVote() {
    }

    public ProductReviewVote(ProductReviewVoteId id, ProductReview review, Users user, Boolean isHelpful) {
        this.id = id;
        this.review = review;
        this.user = user;
        this.isHelpful = isHelpful;
    }

    // =====================
    // JPA Lifecycle Hooks
    // =====================

    @PrePersist
    public void prePersist() {
        this.createdAt = Instant.now();
    }

    // =====================
    // Getters & Setters
    // =====================

    public ProductReviewVoteId getId() {
        return id;
    }

    public void setId(ProductReviewVoteId id) {
        this.id = id;
    }

    public ProductReview getReview() {
        return review;
    }

    public void setReview(ProductReview review) {
        this.review = review;
    }

    public Users getUser() {
        return user;
    }

    public void setUser(Users user) {
        this.user = user;
    }

    public Boolean getIsHelpful() {
        return isHelpful;
    }

    public void setIsHelpful(Boolean isHelpful) {
        this.isHelpful = isHelpful;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
