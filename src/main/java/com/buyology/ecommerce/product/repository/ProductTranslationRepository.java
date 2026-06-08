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

    Optional<ProductTranslation> findByLanguageAndSlug(String language, String slug);

    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END " +
           "FROM ProductTranslation t " +
           "WHERE t.language = :language AND t.slug = :slug AND t.product.status != 'DELETED'")
    boolean existsActiveByLanguageAndSlug(@Param("language") String language, @Param("slug") String slug);
}
