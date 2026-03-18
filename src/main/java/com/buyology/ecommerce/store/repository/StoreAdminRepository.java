package com.buyology.ecommerce.store.repository;

import com.buyology.ecommerce.store.domain.StoreAdmin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StoreAdminRepository extends JpaRepository<StoreAdmin, UUID> {

    List<StoreAdmin> findAllByStoreId(UUID storeId);

    List<StoreAdmin> findAllByStoreIdAndIsActive(UUID storeId, Boolean isActive);

    List<StoreAdmin> findAllByUserId(UUID userId);

    Optional<StoreAdmin> findByStoreIdAndUserId(UUID storeId, UUID userId);

    boolean existsByStoreIdAndUserId(UUID storeId, UUID userId);
}
