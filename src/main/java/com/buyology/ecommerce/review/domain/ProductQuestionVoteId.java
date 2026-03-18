package com.buyology.ecommerce.review.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ProductQuestionVoteId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "question_id")
    private UUID questionId;

    @Column(name = "user_id")
    private UUID userId;

    public ProductQuestionVoteId() {
    }

    public ProductQuestionVoteId(UUID questionId, UUID userId) {
        this.questionId = questionId;
        this.userId = userId;
    }

    public UUID getQuestionId() {
        return questionId;
    }

    public void setQuestionId(UUID questionId) {
        this.questionId = questionId;
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
        if (!(o instanceof ProductQuestionVoteId)) return false;
        ProductQuestionVoteId that = (ProductQuestionVoteId) o;
        return Objects.equals(questionId, that.questionId) &&
               Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(questionId, userId);
    }
}
