package com.buyology.ecommerce.product.repository;

import com.buyology.ecommerce.product.domain.ProductAccessory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductAccessoryRepository extends JpaRepository<ProductAccessory, UUID> {
}
