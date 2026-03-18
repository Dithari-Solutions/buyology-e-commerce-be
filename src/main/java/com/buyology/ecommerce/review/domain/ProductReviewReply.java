package com.buyology.ecommerce.review.domain;

import com.buyology.ecommerce.user.domain.Users;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Admin reply to a review — one per review, enforced by UNIQUE constraint on review_id.
 */
@Entity
@Table(
        name = "product_review_replies",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_prr_review_id", columnNames = {"review_id"})
        }
)
public class ProductReviewReply {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id", nullable = false, unique = true)
    private ProductReview review;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_id", nullable = false)
    private Users admin;

    @Column(name = "body", columnDefinition = "TEXT", nullable = false)
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // =====================
    // Constructors
    // =====================

    public ProductReviewReply() {
    }

    public ProductReviewReply(ProductReview review, Users admin, String body) {
        this.review = review;
        this.admin = admin;
        this.body = body;
    }

    // =====================
    // JPA Lifecycle Hooks
    // =====================

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
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

    public Users getAdmin() {
        return admin;
    }

    public void setAdmin(Users admin) {
        this.admin = admin;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
