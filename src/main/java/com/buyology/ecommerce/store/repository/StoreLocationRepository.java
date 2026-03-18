package com.buyology.ecommerce.store.repository;

import com.buyology.ecommerce.store.domain.StoreLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StoreLocationRepository extends JpaRepository<StoreLocation, UUID> {

    List<StoreLocation> findAllByStoreId(UUID storeId);

    List<StoreLocation> findAllByStoreIdAndIsActive(UUID storeId, Boolean isActive);

    Optional<StoreLocation> findByStoreIdAndIsPrimary(UUID storeId, Boolean isPrimary);

    boolean existsByStoreIdAndIsPrimary(UUID storeId, Boolean isPrimary);
}
