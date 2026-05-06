package com.buyology.ecommerce.review.repository;

import com.buyology.ecommerce.review.domain.ProductReviewStats;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductReviewStatsRepository extends JpaRepository<ProductReviewStats, UUID> {

    Optional<ProductReviewStats> findByProductId(UUID productId);

    List<ProductReviewStats> findByProductIdIn(Collection<UUID> productIds);
}
