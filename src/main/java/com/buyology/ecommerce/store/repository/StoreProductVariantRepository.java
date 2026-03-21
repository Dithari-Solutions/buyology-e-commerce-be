package com.buyology.ecommerce.store.repository;

import com.buyology.ecommerce.store.domain.StoreProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StoreProductVariantRepository extends JpaRepository<StoreProductVariant, UUID> {

    Optional<StoreProductVariant> findByStoreProduct_IdAndVariant_Id(UUID storeProductId, UUID variantId);
}
