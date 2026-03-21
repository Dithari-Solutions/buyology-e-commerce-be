package com.buyology.ecommerce.store.repository;

import com.buyology.ecommerce.store.domain.StoreLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * Returns distinct store IDs whose active locations fall within the given
     * radius (km) of the provided coordinates, using the Haversine formula.
     * Earth radius used: 6371 km.
     */
    @Query(value = """
            SELECT DISTINCT sl.store_id FROM store_locations sl
            WHERE sl.is_active = true
              AND (6371 * acos(LEAST(1.0,
                    cos(radians(:lat)) * cos(radians(sl.latitude))
                    * cos(radians(sl.longitude) - radians(:lng))
                    + sin(radians(:lat)) * sin(radians(sl.latitude))
                  ))) <= :radiusKm
            """, nativeQuery = true)
    List<UUID> findStoreIdsWithinRadius(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radiusKm") double radiusKm);
}
