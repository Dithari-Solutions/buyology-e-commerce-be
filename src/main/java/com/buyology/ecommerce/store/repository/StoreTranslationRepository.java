package com.buyology.ecommerce.store.repository;

import com.buyology.ecommerce.store.domain.StoreTranslation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StoreTranslationRepository extends JpaRepository<StoreTranslation, UUID> {

    List<StoreTranslation> findAllByStoreId(UUID storeId);

    Optional<StoreTranslation> findByStoreIdAndLanguage(UUID storeId, String language);

    boolean existsByStoreIdAndLanguage(UUID storeId, String language);
}
