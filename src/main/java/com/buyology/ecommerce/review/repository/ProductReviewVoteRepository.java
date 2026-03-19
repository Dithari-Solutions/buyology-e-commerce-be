package com.buyology.ecommerce.review.repository;

import com.buyology.ecommerce.review.domain.ProductReviewVote;
import com.buyology.ecommerce.review.domain.ProductReviewVoteId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProductReviewVoteRepository extends JpaRepository<ProductReviewVote, ProductReviewVoteId> {

    Optional<ProductReviewVote> findByIdReviewIdAndIdUserId(UUID reviewId, UUID userId);

    boolean existsByIdReviewIdAndIdUserId(UUID reviewId, UUID userId);
}
