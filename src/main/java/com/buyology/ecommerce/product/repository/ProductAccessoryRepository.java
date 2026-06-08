package com.buyology.ecommerce.product.repository;

import com.buyology.ecommerce.product.domain.ProductAccessory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductAccessoryRepository extends JpaRepository<ProductAccessory, UUID> {

    List<ProductAccessory> findByProductId(UUID productId);

    List<ProductAccessory> findByProductIdIn(List<UUID> productIds);

    List<ProductAccessory> findByAccessoryId(UUID accessoryId);
}
