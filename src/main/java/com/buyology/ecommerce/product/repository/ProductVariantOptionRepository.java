package com.buyology.ecommerce.product.repository;

import com.buyology.ecommerce.product.domain.ProductVariantOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductVariantOptionRepository extends JpaRepository<ProductVariantOption, UUID> {
}
