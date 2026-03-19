package com.buyology.ecommerce.review.repository;

import com.buyology.ecommerce.review.domain.ProductReview;
import com.buyology.ecommerce.review.domain.enums.ModerationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductReviewRepository extends JpaRepository<ProductReview, UUID> {

    Page<ProductReview> findByProductIdAndStatusAndDeletedAtIsNull(UUID productId, ModerationStatus status, Pageable pageable);

    List<ProductReview> findByProductIdAndStatusAndDeletedAtIsNull(UUID productId, ModerationStatus status);

    Optional<ProductReview> findByIdAndDeletedAtIsNull(UUID id);

    Optional<ProductReview> findByProductIdAndUserIdAndDeletedAtIsNull(UUID productId, UUID userId);

    boolean existsByProductIdAndUserIdAndDeletedAtIsNull(UUID productId, UUID userId);

    Page<ProductReview> findByStatusAndDeletedAtIsNull(ModerationStatus status, Pageable pageable);

    Page<ProductReview> findByDeletedAtIsNull(Pageable pageable);

    Page<ProductReview> findByStatus(ModerationStatus status, Pageable pageable);
}
