package com.buyology.ecommerce.store.repository;

import com.buyology.ecommerce.store.domain.Store;
import com.buyology.ecommerce.store.enums.StoreStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StoreRepository extends JpaRepository<Store, UUID> {

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, UUID excludeId);

    Optional<Store> findBySlugAndDeletedAtIsNull(String slug);

    Optional<Store> findByIdAndDeletedAtIsNull(UUID id);

    List<Store> findAllByDeletedAtIsNull();

    List<Store> findAllByStatusAndDeletedAtIsNull(StoreStatus status);

    List<Store> findAllByCountryIdAndDeletedAtIsNull(UUID countryId);
}
