package com.buyology.ecommerce.review.repository;

import com.buyology.ecommerce.review.domain.ProductQuestion;
import com.buyology.ecommerce.review.domain.enums.ModerationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductQuestionRepository extends JpaRepository<ProductQuestion, UUID> {

    Page<ProductQuestion> findByProductIdAndStatusAndDeletedAtIsNull(UUID productId, ModerationStatus status, Pageable pageable);

    List<ProductQuestion> findByProductIdAndStatusAndDeletedAtIsNull(UUID productId, ModerationStatus status);

    Optional<ProductQuestion> findByIdAndDeletedAtIsNull(UUID id);

    Page<ProductQuestion> findByStatusAndDeletedAtIsNull(ModerationStatus status, Pageable pageable);

    Page<ProductQuestion> findByDeletedAtIsNull(Pageable pageable);
}
