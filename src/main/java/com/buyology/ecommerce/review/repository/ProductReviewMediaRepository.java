package com.buyology.ecommerce.review.repository;

import com.buyology.ecommerce.review.domain.ProductReviewMedia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductReviewMediaRepository extends JpaRepository<ProductReviewMedia, UUID> {

    List<ProductReviewMedia> findByReviewIdOrderBySortOrderAsc(UUID reviewId);

    void deleteByReviewId(UUID reviewId);
}
