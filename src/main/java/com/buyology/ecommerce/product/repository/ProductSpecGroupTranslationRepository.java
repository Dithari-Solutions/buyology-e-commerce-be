package com.buyology.ecommerce.product.repository;

import com.buyology.ecommerce.product.domain.ProductSpecGroupTranslation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductSpecGroupTranslationRepository extends JpaRepository<ProductSpecGroupTranslation, UUID> {
}
