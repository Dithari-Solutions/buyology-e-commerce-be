package com.buyology.ecommerce.courier.profile.repository;

import com.buyology.ecommerce.courier.profile.domain.CourierProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CourierProfileRepository extends JpaRepository<CourierProfile, UUID> {

    List<CourierProfile> findByStoreIdOrderByCreatedAtDesc(UUID storeId);

    List<CourierProfile> findByStoreIdAndActiveTrueOrderByFirstNameAsc(UUID storeId);
}
