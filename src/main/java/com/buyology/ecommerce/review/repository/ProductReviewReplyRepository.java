package com.buyology.ecommerce.review.repository;

import com.buyology.ecommerce.review.domain.ProductReviewReply;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProductReviewReplyRepository extends JpaRepository<ProductReviewReply, UUID> {

    Optional<ProductReviewReply> findByReviewId(UUID reviewId);

    boolean existsByReviewId(UUID reviewId);
}
