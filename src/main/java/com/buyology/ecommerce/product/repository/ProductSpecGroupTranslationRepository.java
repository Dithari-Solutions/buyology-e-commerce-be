package com.buyology.ecommerce.product.repository;

import com.buyology.ecommerce.product.domain.ProductSpecGroupTranslation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductSpecGroupTranslationRepository extends JpaRepository<ProductSpecGroupTranslation, UUID> {

    Optional<ProductSpecGroupTranslation> findByGroup_IdAndLanguageIgnoreCase(UUID groupId, String language);

    List<ProductSpecGroupTranslation> findAllByGroup_Id(UUID groupId);
}
