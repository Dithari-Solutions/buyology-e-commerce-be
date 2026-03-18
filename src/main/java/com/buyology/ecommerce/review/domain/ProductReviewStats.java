package com.buyology.ecommerce.review.domain;

import com.buyology.ecommerce.product.domain.Product;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Pre-aggregated review statistics per product.
 *
 * Uses a shared primary key pattern: product_id is both the PK and the FK to products.
 * @Id has NO @GeneratedValue — the PK value is derived from the associated Product via @MapsId.
 *
 * Updated asynchronously after each review approval/deletion event.
 * This avoids AVG + COUNT aggregates on the hot product-page read path.
 */
@Entity
@Table(name = "product_review_stats")
public class ProductReviewStats {

    @Id
    @Column(name = "product_id", updatable = false, nullable = false)
    private UUID productId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "total_reviews", nullable = false)
    private Integer totalReviews = 0;

    // NUMERIC(3,2) avoids floating-point rounding errors (e.g. 3.67 not 3.6700000000000004)
    @Column(name = "average_rating", precision = 3, scale = 2, nullable = false)
    private BigDecimal averageRating = BigDecimal.ZERO;

    @Column(name = "rating_1_count", nullable = false)
    private Integer rating1Count = 0;

    @Column(name = "rating_2_count", nullable = false)
    private Integer rating2Count = 0;

    @Column(name = "rating_3_count", nullable = false)
    private Integer rating3Count = 0;

    @Column(name = "rating_4_count", nullable = false)
    private Integer rating4Count = 0;

    @Column(name = "rating_5_count", nullable = false)
    private Integer rating5Count = 0;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // =====================
    // Constructors
    // =====================

    public ProductReviewStats() {
    }

    public ProductReviewStats(Product product) {
        this.product = product;
    }

    // =====================
    // JPA Lifecycle Hooks
    // =====================

    @PrePersist
    public void prePersist() {
        this.updatedAt = Instant.now();
        if (this.totalReviews == null) this.totalReviews = 0;
        if (this.averageRating == null) this.averageRating = BigDecimal.ZERO;
        if (this.rating1Count == null) this.rating1Count = 0;
        if (this.rating2Count == null) this.rating2Count = 0;
        if (this.rating3Count == null) this.rating3Count = 0;
        if (this.rating4Count == null) this.rating4Count = 0;
        if (this.rating5Count == null) this.rating5Count = 0;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    // =====================
    // Getters & Setters
    // =====================

    public UUID getProductId() {
        return productId;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public Integer getTotalReviews() {
        return totalReviews;
    }

    public void setTotalReviews(Integer totalReviews) {
        this.totalReviews = totalReviews;
    }

    public BigDecimal getAverageRating() {
        return averageRating;
    }

    public void setAverageRating(BigDecimal averageRating) {
        this.averageRating = averageRating;
    }

    public Integer getRating1Count() {
        return rating1Count;
    }

    public void setRating1Count(Integer rating1Count) {
        this.rating1Count = rating1Count;
    }

    public Integer getRating2Count() {
        return rating2Count;
    }

    public void setRating2Count(Integer rating2Count) {
        this.rating2Count = rating2Count;
    }

    public Integer getRating3Count() {
        return rating3Count;
    }

    public void setRating3Count(Integer rating3Count) {
        this.rating3Count = rating3Count;
    }

    public Integer getRating4Count() {
        return rating4Count;
    }

    public void setRating4Count(Integer rating4Count) {
        this.rating4Count = rating4Count;
    }

    public Integer getRating5Count() {
        return rating5Count;
    }

    public void setRating5Count(Integer rating5Count) {
        this.rating5Count = rating5Count;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
