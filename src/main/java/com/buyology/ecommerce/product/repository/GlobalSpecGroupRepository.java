package com.buyology.ecommerce.product.repository;

import com.buyology.ecommerce.product.domain.GlobalSpecGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GlobalSpecGroupRepository extends JpaRepository<GlobalSpecGroup, UUID> {

    boolean existsByCode(String code);

    Optional<GlobalSpecGroup> findByCode(String code);
}
