package com.buyology.ecommerce.product.repository;

import com.buyology.ecommerce.product.domain.ProductTranslation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductTranslationRepository extends JpaRepository<ProductTranslation, UUID> {

    List<ProductTranslation> findByProductId(UUID productId);
}
