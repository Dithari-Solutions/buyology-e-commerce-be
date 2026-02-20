package com.buyology.ecommerce.product.repository;

import com.buyology.ecommerce.product.domain.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductCategoryRepository extends JpaRepository<ProductCategory, UUID> {
}
