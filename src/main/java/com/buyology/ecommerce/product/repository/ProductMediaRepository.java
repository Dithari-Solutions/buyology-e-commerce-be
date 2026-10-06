package com.buyology.ecommerce.product.repository;

import com.buyology.ecommerce.product.domain.ProductMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductMediaRepository extends JpaRepository<ProductMedia, UUID> {

    List<ProductMedia> findByProductId(UUID productId);

    List<ProductMedia> findByProductIdIn(List<UUID> productIds);

    List<ProductMedia> findByProductIdAndColorOptionId(UUID productId, UUID colorOptionId);

    List<ProductMedia> findByProductIdAndColorOptionIsNull(UUID productId);

    /** The stored URL of a media item, unless its product has been deleted. */
    @Query("SELECT m.url FROM ProductMedia m WHERE m.id = :id AND m.product.status <> 'DELETED'")
    Optional<String> findUrlOfLiveProductMedia(@Param("id") UUID id);
}
