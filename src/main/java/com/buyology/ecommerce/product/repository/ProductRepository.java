package com.buyology.ecommerce.product.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.buyology.ecommerce.product.domain.Product;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    List<Product> findByCategoryId(UUID categoryId);

    List<Product> findByStatus(String status);

    List<Product> findByStatusAndCategoryId(String status, UUID categoryId);

    List<Product> findByStatusNot(String status);

    List<Product> findByStatusNotAndCategoryId(String status, UUID categoryId);

    List<Product> findByStatusAndDeletedAtBefore(String status, Instant cutoff);

    boolean existsBySku(String sku);
}
