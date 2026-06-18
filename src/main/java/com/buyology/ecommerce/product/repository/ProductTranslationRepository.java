package com.buyology.ecommerce.product.repository;

import com.buyology.ecommerce.product.domain.ProductTranslation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductTranslationRepository extends JpaRepository<ProductTranslation, UUID> {

    List<ProductTranslation> findByProductId(UUID productId);

    List<ProductTranslation> findByProductIdIn(List<UUID> productIds);

    /**
     * Lightweight (productId, language, title) rows for the given products — used to label
     * order-confirmation emails without initialising the Product proxies. Each row is
     * [UUID productId, String language, String title].
     */
    @Query("SELECT t.product.id, t.language, t.title FROM ProductTranslation t WHERE t.product.id IN :ids")
    List<Object[]> findTitleRowsByProductIds(@Param("ids") List<UUID> ids);

    Optional<ProductTranslation> findByLanguageAndSlug(String language, String slug);

    /** Resolve a product by its slug in ANY language (slugs are per-language), restricted to active products. */
    @Query("SELECT t FROM ProductTranslation t WHERE t.slug = :slug AND t.product.status = 'ACTIVE' ORDER BY t.language")
    List<ProductTranslation> findActiveBySlugAnyLang(@Param("slug") String slug);

    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END " +
           "FROM ProductTranslation t " +
           "WHERE t.language = :language AND t.slug = :slug AND t.product.status != 'DELETED'")
    boolean existsActiveByLanguageAndSlug(@Param("language") String language, @Param("slug") String slug);

    /** True if a non-deleted product (other than excludeProductId, when given) already uses this name in this language. */
    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END " +
           "FROM ProductTranslation t " +
           "WHERE t.language = :language AND LOWER(t.title) = LOWER(:title) " +
           "AND t.product.status != 'DELETED' " +
           "AND (:excludeProductId IS NULL OR t.product.id <> :excludeProductId)")
    boolean existsActiveByLanguageAndTitleIgnoreCase(@Param("language") String language,
            @Param("title") String title, @Param("excludeProductId") UUID excludeProductId);
}
