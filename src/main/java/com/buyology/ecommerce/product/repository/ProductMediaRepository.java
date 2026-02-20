package com.buyology.ecommerce.product.repository;

import com.buyology.ecommerce.product.domain.ProductMedia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductMediaRepository extends JpaRepository<ProductMedia, UUID> {
}
