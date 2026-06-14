package com.buyology.ecommerce.product.repository;

import com.buyology.ecommerce.product.domain.SpecCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpecCodeRepository extends JpaRepository<SpecCode, UUID> {
    List<SpecCode> findAllByOrderByDisplayOrderAscCodeAsc();
    boolean existsByCodeIgnoreCase(String code);
    Optional<SpecCode> findByCodeIgnoreCase(String code);
}
