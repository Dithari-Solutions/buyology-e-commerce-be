package com.buyology.ecommerce.product.repository;

import com.buyology.ecommerce.product.domain.ProductSpecOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductSpecOptionRepository extends JpaRepository<ProductSpecOption, UUID> {
}
