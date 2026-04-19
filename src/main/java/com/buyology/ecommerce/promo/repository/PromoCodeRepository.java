package com.buyology.ecommerce.promo.repository;

import com.buyology.ecommerce.promo.domain.PromoCode;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface PromoCodeRepository extends JpaRepository<PromoCode, UUID> {
    Optional<PromoCode> findByCodeIgnoreCaseAndIsActiveTrue(String code);
    boolean existsByCodeIgnoreCase(String code);
}
