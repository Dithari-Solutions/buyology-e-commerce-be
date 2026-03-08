package com.buyology.ecommerce.product.repository;

import com.buyology.ecommerce.product.domain.ProductSpecOptionTranslation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductSpecOptionTranslationRepository extends JpaRepository<ProductSpecOptionTranslation, UUID> {
}
