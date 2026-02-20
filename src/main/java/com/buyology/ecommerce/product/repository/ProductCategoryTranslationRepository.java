package com.buyology.ecommerce.product.repository;

import com.buyology.ecommerce.product.domain.ProductCategoryTranslation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductCategoryTranslationRepository extends JpaRepository<ProductCategoryTranslation, UUID> {

    boolean existsBySlugAndLanguage(String slug, String language);
}
