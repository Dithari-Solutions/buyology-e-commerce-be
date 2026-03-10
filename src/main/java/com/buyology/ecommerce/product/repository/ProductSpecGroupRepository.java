package com.buyology.ecommerce.product.repository;

import com.buyology.ecommerce.product.domain.ProductSpecGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductSpecGroupRepository extends JpaRepository<ProductSpecGroup, UUID> {

    List<ProductSpecGroup> findByProduct_Id(UUID productId);
}
