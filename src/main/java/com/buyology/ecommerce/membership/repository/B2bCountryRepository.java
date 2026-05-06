package com.buyology.ecommerce.membership.repository;

import com.buyology.ecommerce.membership.domain.B2bCountry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface B2bCountryRepository extends JpaRepository<B2bCountry, UUID> {

    Optional<B2bCountry> findByCountryCode(String countryCode);

    List<B2bCountry> findByEnabledTrueOrderByCountryNameAsc();
}
