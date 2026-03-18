package com.buyology.ecommerce.store.repository;

import com.buyology.ecommerce.store.domain.Country;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CountryRepository extends JpaRepository<Country, UUID> {

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, UUID excludeId);

    Optional<Country> findByCode(String code);

    List<Country> findAllByIsActive(Boolean isActive);
}
