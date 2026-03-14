package com.buyology.ecommerce.product.repository;

import com.buyology.ecommerce.product.domain.ProductCategoryTranslation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductCategoryTranslationRepository extends JpaRepository<ProductCategoryTranslation, UUID> {

    boolean existsBySlugAndLanguage(String slug, String language);

    boolean existsBySlugAndLanguageAndIdNot(String slug, String language, UUID excludeId);

    Optional<ProductCategoryTranslation> findByCategoryIdAndLanguage(UUID categoryId, String language);

    List<ProductCategoryTranslation> findAllByCategoryId(UUID categoryId);
}
