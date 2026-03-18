package com.buyology.ecommerce.review.domain;

import com.buyology.ecommerce.user.domain.Users;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Admin answer to a question — one per question, enforced by UNIQUE constraint on question_id.
 *
 * isActive = lightweight operational toggle (hide while revising, no audit trail impact).
 * deletedAt  = permanent soft-delete with full audit trail.
 */
@Entity
@Table(
        name = "product_question_answers",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_pqa_question_id", columnNames = {"question_id"})
        },
        indexes = {
                @Index(name = "idx_pqa_question_id", columnList = "question_id")
        }
)
public class ProductQuestionAnswer {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false, unique = true)
    private ProductQuestion question;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_id", nullable = false)
    private Users admin;

    @Column(name = "body", columnDefinition = "TEXT", nullable = false)
    private String body;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // =====================
    // Constructors
    // =====================

    public ProductQuestionAnswer() {
    }

    public ProductQuestionAnswer(ProductQuestion question, Users admin, String body) {
        this.question = question;
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

        if (this.isActive == null) this.isActive = true;
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

    public ProductQuestion getQuestion() {
        return question;
    }

    public void setQuestion(ProductQuestion question) {
        this.question = question;
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

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
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
