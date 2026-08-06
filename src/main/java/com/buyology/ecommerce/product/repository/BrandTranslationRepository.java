package com.buyology.ecommerce.product.repository;

import com.buyology.ecommerce.product.domain.BrandTranslation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BrandTranslationRepository extends JpaRepository<BrandTranslation, UUID> {

    List<BrandTranslation> findAllByBrand_Id(UUID brandId);

    Optional<BrandTranslation> findByBrand_IdAndLanguageIgnoreCase(UUID brandId, String language);

    /** Resolve a brand by its name in a given language — used by the ERPNext catalog import. */
    Optional<BrandTranslation> findFirstByLanguageIgnoreCaseAndNameIgnoreCase(String language, String name);
}
