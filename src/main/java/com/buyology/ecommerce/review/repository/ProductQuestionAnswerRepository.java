package com.buyology.ecommerce.review.repository;

import com.buyology.ecommerce.review.domain.ProductQuestionAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProductQuestionAnswerRepository extends JpaRepository<ProductQuestionAnswer, UUID> {

    Optional<ProductQuestionAnswer> findByQuestionIdAndDeletedAtIsNull(UUID questionId);

    boolean existsByQuestionIdAndDeletedAtIsNull(UUID questionId);
}
