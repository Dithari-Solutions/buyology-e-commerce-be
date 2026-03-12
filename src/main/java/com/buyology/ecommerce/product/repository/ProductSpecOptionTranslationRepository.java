package com.buyology.ecommerce.product.repository;

import com.buyology.ecommerce.common.enums.Language;
import com.buyology.ecommerce.product.domain.ProductSpecOptionTranslation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductSpecOptionTranslationRepository extends JpaRepository<ProductSpecOptionTranslation, UUID> {

    Optional<ProductSpecOptionTranslation> findByOption_IdAndLanguage(UUID optionId, Language language);

    List<ProductSpecOptionTranslation> findAllByOption_Id(UUID optionId);
}
