package com.buyology.ecommerce.warranty.repository;

import com.buyology.ecommerce.warranty.domain.WarrantyPreset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WarrantyPresetRepository extends JpaRepository<WarrantyPreset, UUID> {

    List<WarrantyPreset> findAllByOrderByDurationMonthsAscNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
}
