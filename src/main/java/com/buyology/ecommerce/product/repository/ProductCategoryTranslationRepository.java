package com.buyology.ecommerce.product.repository;

import com.buyology.ecommerce.product.domain.ProductCategoryTranslation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductCategoryTranslationRepository extends JpaRepository<ProductCategoryTranslation, UUID> {

    boolean existsBySlugAndLanguage(String slug, String language);

    boolean existsBySlugAndLanguageAndIdNot(String slug, String language, UUID excludeId);

    Optional<ProductCategoryTranslation> findByCategoryIdAndLanguage(UUID categoryId, String language);

    /** Resolve a category by its name in a given language — used by the ERPNext catalog import. */
    Optional<ProductCategoryTranslation> findFirstByLanguageIgnoreCaseAndNameIgnoreCase(String language, String name);

    List<ProductCategoryTranslation> findAllByCategoryId(UUID categoryId);

    /**
     * Category ids whose name (in the given language) contains the term, case-insensitive.
     * Used to resolve the laptop category/categories for the WELCOME10 exclusion.
     */
    @Query("SELECT DISTINCT t.category.id FROM ProductCategoryTranslation t "
            + "WHERE LOWER(t.language) = LOWER(:language) AND LOWER(t.name) LIKE LOWER(CONCAT('%', :term, '%'))")
    List<UUID> findCategoryIdsByNameContaining(@Param("language") String language, @Param("term") String term);
}
