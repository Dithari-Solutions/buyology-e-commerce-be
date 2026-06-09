package com.buyology.ecommerce.promo.repository;

import com.buyology.ecommerce.promo.domain.PromoCode;
import com.buyology.ecommerce.promo.domain.PromoCodeUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PromoCodeUsageRepository extends JpaRepository<PromoCodeUsage, UUID> {
    long countByPromoCode(PromoCode promoCode);
    long countByPromoCodeAndUserId(PromoCode promoCode, UUID userId);
    List<PromoCodeUsage> findByPromoCode_IdOrderByUsedAtDesc(UUID promoCodeId);
}
