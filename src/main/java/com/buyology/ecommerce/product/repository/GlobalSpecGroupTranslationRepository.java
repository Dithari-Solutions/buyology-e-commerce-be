package com.buyology.ecommerce.product.repository;

import com.buyology.ecommerce.product.domain.GlobalSpecGroupTranslation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GlobalSpecGroupTranslationRepository extends JpaRepository<GlobalSpecGroupTranslation, UUID> {

    List<GlobalSpecGroupTranslation> findAllByGroup_Id(UUID groupId);

    Optional<GlobalSpecGroupTranslation> findByGroup_IdAndLanguageIgnoreCase(UUID groupId, String language);

    java.util.List<GlobalSpecGroupTranslation> findByGroup_IdInAndLanguageIgnoreCase(java.util.List<UUID> groupIds, String language);
}
