package com.buyology.ecommerce.review.domain;

import com.buyology.ecommerce.review.domain.enums.MediaType;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Photos or videos attached to a review.
 * Rows are immutable once created — no updatedAt.
 */
@Entity
@Table(
        name = "product_review_media",
        indexes = {
                @Index(name = "idx_prm_review_id", columnList = "review_id")
        }
)
public class ProductReviewMedia {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false)
    private ProductReview review;

    @Column(name = "url", columnDefinition = "TEXT", nullable = false)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 10)
    private MediaType mediaType;

    @Column(name = "sort_order", columnDefinition = "SMALLINT", nullable = false)
    private Short sortOrder = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // =====================
    // Constructors
    // =====================

    public ProductReviewMedia() {
    }

    public ProductReviewMedia(ProductReview review, String url, MediaType mediaType, Short sortOrder) {
        this.review = review;
        this.url = url;
        this.mediaType = mediaType;
        this.sortOrder = sortOrder;
    }

    // =====================
    // JPA Lifecycle Hooks
    // =====================

    @PrePersist
    public void prePersist() {
        this.createdAt = Instant.now();
        if (this.sortOrder == null) this.sortOrder = 0;
    }

    // =====================
    // Getters & Setters
    // =====================

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ProductReview getReview() {
        return review;
    }

    public void setReview(ProductReview review) {
        this.review = review;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public MediaType getMediaType() {
        return mediaType;
    }

    public void setMediaType(MediaType mediaType) {
        this.mediaType = mediaType;
    }

    public Short getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Short sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
