package com.buyology.ecommerce.review.domain;

import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.review.domain.enums.ModerationStatus;
import com.buyology.ecommerce.user.domain.Users;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.Instant;
import java.util.UUID;

/**
 * One review per user per product.
 *
 * NOTE — partial indexes for the hot read path cannot be expressed via JPA @Index.
 * After initial schema creation apply these manually:
 *
 *   CREATE INDEX CONCURRENTLY idx_pr_product_approved
 *     ON product_reviews(product_id, created_at DESC)
 *     WHERE status = 'APPROVED' AND deleted_at IS NULL;
 *
 *   CREATE INDEX CONCURRENTLY idx_pr_product_rating_approved
 *     ON product_reviews(product_id, rating)
 *     WHERE status = 'APPROVED' AND deleted_at IS NULL;
 */
@Entity
@Table(
        name = "product_reviews",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_product_reviews_product_user",
                        columnNames = {"product_id", "user_id"}
                )
        },
        indexes = {
                @Index(name = "idx_pr_product_created",  columnList = "product_id, created_at DESC"),
                @Index(name = "idx_pr_product_rating",   columnList = "product_id, rating"),
                @Index(name = "idx_pr_status_created",   columnList = "status, created_at")
        }
)
public class ProductReview {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Min(1)
    @Max(5)
    @Column(name = "rating", columnDefinition = "SMALLINT", nullable = false)
    private Short rating;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Column(name = "is_verified_purchase", nullable = false)
    private Boolean isVerifiedPurchase = false;

    // Raw UUID — no FK. Order module may be a separate service.
    @Column(name = "order_item_id")
    private UUID orderItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ModerationStatus status = ModerationStatus.PENDING;

    // Raw UUID — no FK. Admin tooling may be an external service.
    @Column(name = "moderated_by")
    private UUID moderatedBy;

    @Column(name = "moderated_at")
    private Instant moderatedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "helpful_count", nullable = false)
    private Integer helpfulCount = 0;

    @Column(name = "not_helpful_count", nullable = false)
    private Integer notHelpfulCount = 0;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // =====================
    // Constructors
    // =====================

    public ProductReview() {
    }

    public ProductReview(Product product, Users user, Short rating, String body) {
        this.product = product;
        this.user = user;
        this.rating = rating;
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

        if (this.isVerifiedPurchase == null) this.isVerifiedPurchase = false;
        if (this.status == null) this.status = ModerationStatus.PENDING;
        if (this.helpfulCount == null) this.helpfulCount = 0;
        if (this.notHelpfulCount == null) this.notHelpfulCount = 0;
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

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public Users getUser() {
        return user;
    }

    public void setUser(Users user) {
        this.user = user;
    }

    public Short getRating() {
        return rating;
    }

    public void setRating(Short rating) {
        this.rating = rating;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Boolean getIsVerifiedPurchase() {
        return isVerifiedPurchase;
    }

    public void setIsVerifiedPurchase(Boolean isVerifiedPurchase) {
        this.isVerifiedPurchase = isVerifiedPurchase;
    }

    public UUID getOrderItemId() {
        return orderItemId;
    }

    public void setOrderItemId(UUID orderItemId) {
        this.orderItemId = orderItemId;
    }

    public ModerationStatus getStatus() {
        return status;
    }

    public void setStatus(ModerationStatus status) {
        this.status = status;
    }

    public UUID getModeratedBy() {
        return moderatedBy;
    }

    public void setModeratedBy(UUID moderatedBy) {
        this.moderatedBy = moderatedBy;
    }

    public Instant getModeratedAt() {
        return moderatedAt;
    }

    public void setModeratedAt(Instant moderatedAt) {
        this.moderatedAt = moderatedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public Integer getHelpfulCount() {
        return helpfulCount;
    }

    public void setHelpfulCount(Integer helpfulCount) {
        this.helpfulCount = helpfulCount;
    }

    public Integer getNotHelpfulCount() {
        return notHelpfulCount;
    }

    public void setNotHelpfulCount(Integer notHelpfulCount) {
        this.notHelpfulCount = notHelpfulCount;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
