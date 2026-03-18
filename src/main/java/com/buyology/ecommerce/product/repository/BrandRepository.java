package com.buyology.ecommerce.product.repository;

import com.buyology.ecommerce.product.domain.Brand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BrandRepository extends JpaRepository<Brand, UUID> {

    List<Brand> findByStatus(String status);
}
