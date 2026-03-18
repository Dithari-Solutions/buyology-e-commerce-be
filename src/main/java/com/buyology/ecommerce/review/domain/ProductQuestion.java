package com.buyology.ecommerce.review.domain;

import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.review.domain.enums.ModerationStatus;
import com.buyology.ecommerce.user.domain.Users;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A question asked by a user about a product.
 *
 * No UNIQUE(product_id, user_id) — a user may legitimately ask multiple questions.
 *
 * NOTE — partial index for the hot read path cannot be expressed via JPA @Index.
 * Apply manually after schema creation:
 *
 *   CREATE INDEX CONCURRENTLY idx_pq_product_approved
 *     ON product_questions(product_id, helpful_count DESC, created_at DESC)
 *     WHERE status = 'APPROVED' AND deleted_at IS NULL;
 */
@Entity
@Table(
        name = "product_questions",
        indexes = {
                @Index(name = "idx_pq_product_helpful_created", columnList = "product_id, helpful_count DESC, created_at DESC"),
                @Index(name = "idx_pq_status_created",          columnList = "status, created_at")
        }
)
public class ProductQuestion {

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

    @Column(name = "body", columnDefinition = "TEXT", nullable = false)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ModerationStatus status = ModerationStatus.PENDING;

    // Raw UUID — no FK. Admin tooling may be an external service.
    @Column(name = "moderated_by")
    private UUID moderatedBy;

    @Column(name = "moderated_at")
    private Instant moderatedAt;

    @Column(name = "helpful_count", nullable = false)
    private Integer helpfulCount = 0;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // =====================
    // Constructors
    // =====================

    public ProductQuestion() {
    }

    public ProductQuestion(Product product, Users user, String body) {
        this.product = product;
        this.user = user;
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

        if (this.status == null) this.status = ModerationStatus.PENDING;
        if (this.helpfulCount == null) this.helpfulCount = 0;
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

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
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

    public Integer getHelpfulCount() {
        return helpfulCount;
    }

    public void setHelpfulCount(Integer helpfulCount) {
        this.helpfulCount = helpfulCount;
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
