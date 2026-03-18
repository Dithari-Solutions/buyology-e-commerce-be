package com.buyology.ecommerce.product.repository;

import com.buyology.ecommerce.common.enums.Language;
import com.buyology.ecommerce.product.domain.GlobalSpecOptionTranslation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GlobalSpecOptionTranslationRepository extends JpaRepository<GlobalSpecOptionTranslation, UUID> {

    List<GlobalSpecOptionTranslation> findAllByOption_Id(UUID optionId);

    Optional<GlobalSpecOptionTranslation> findByOption_IdAndLanguage(UUID optionId, Language language);
}
