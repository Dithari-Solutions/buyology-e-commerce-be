package com.buyology.ecommerce.product.repository;

import com.buyology.ecommerce.product.domain.ProductMedia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductMediaRepository extends JpaRepository<ProductMedia, UUID> {

    List<ProductMedia> findByProductId(UUID productId);

    List<ProductMedia> findByProductIdAndColorOptionId(UUID productId, UUID colorOptionId);

    List<ProductMedia> findByProductIdAndColorOptionIsNull(UUID productId);
}
