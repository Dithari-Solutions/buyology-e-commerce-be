package com.buyology.ecommerce.review.repository;

import com.buyology.ecommerce.review.domain.ProductQuestionVote;
import com.buyology.ecommerce.review.domain.ProductQuestionVoteId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProductQuestionVoteRepository extends JpaRepository<ProductQuestionVote, ProductQuestionVoteId> {

    Optional<ProductQuestionVote> findByIdQuestionIdAndIdUserId(UUID questionId, UUID userId);

    boolean existsByIdQuestionIdAndIdUserId(UUID questionId, UUID userId);
}
