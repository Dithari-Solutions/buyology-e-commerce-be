package com.buyology.ecommerce.store.repository;

import com.buyology.ecommerce.store.domain.StoreOperatingHours;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StoreOperatingHoursRepository extends JpaRepository<StoreOperatingHours, UUID> {

    List<StoreOperatingHours> findAllByLocationId(UUID locationId);

    Optional<StoreOperatingHours> findByLocationIdAndDayOfWeek(UUID locationId, DayOfWeek dayOfWeek);

    boolean existsByLocationIdAndDayOfWeek(UUID locationId, DayOfWeek dayOfWeek);
}
