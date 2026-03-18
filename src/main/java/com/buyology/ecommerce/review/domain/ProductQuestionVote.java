package com.buyology.ecommerce.review.domain;

import com.buyology.ecommerce.user.domain.Users;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * Records that a user found a question helpful.
 *
 * Composite PK (question_id + user_id) prevents double-voting at the DB level.
 * No isHelpful column — the vote's existence implies the question was helpful.
 *
 * @MapsId must reference the Java field name inside the @Embeddable, not the column name.
 * insertable=false + updatable=false on both @JoinColumn declarations is mandatory
 * to avoid Hibernate "repeated column in mapping" startup error.
 */
@Entity
@Table(name = "product_question_votes")
public class ProductQuestionVote {

    @EmbeddedId
    private ProductQuestionVoteId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("questionId")
    @JoinColumn(name = "question_id", insertable = false, updatable = false)
    private ProductQuestion question;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("userId")
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private Users user;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // =====================
    // Constructors
    // =====================

    public ProductQuestionVote() {
    }

    public ProductQuestionVote(ProductQuestionVoteId id, ProductQuestion question, Users user) {
        this.id = id;
        this.question = question;
        this.user = user;
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

    public ProductQuestionVoteId getId() {
        return id;
    }

    public void setId(ProductQuestionVoteId id) {
        this.id = id;
    }

    public ProductQuestion getQuestion() {
        return question;
    }

    public void setQuestion(ProductQuestion question) {
        this.question = question;
    }

    public Users getUser() {
        return user;
    }

    public void setUser(Users user) {
        this.user = user;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
