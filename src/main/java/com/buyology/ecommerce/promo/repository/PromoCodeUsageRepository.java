package com.buyology.ecommerce.promo.repository;

import com.buyology.ecommerce.promo.domain.PromoCode;
import com.buyology.ecommerce.promo.domain.PromoCodeUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PromoCodeUsageRepository extends JpaRepository<PromoCodeUsage, UUID> {

    /**
     * Every claim on the code, held or spent.
     *
     * <p>Counts reservations as well as redemptions, which is the whole point: an order that has
     * been placed but not yet paid for is still going to consume this code, and leaving it out of
     * the count is what let one single-use code be spent on order after order.
     */
    long countByPromoCode(PromoCode promoCode);

    long countByPromoCodeAndUserId(PromoCode promoCode, UUID userId);

    List<PromoCodeUsage> findByPromoCode_IdOrderByUsedAtDesc(UUID promoCodeId);

    /** Whether this customer is already holding the code on an order they have not paid for. */
    boolean existsByPromoCode_IdAndUserIdAndStatus(UUID promoCodeId, UUID userId,
                                                  PromoCodeUsage.Status status);

    /** The claim a specific order has on a code, reserved or redeemed. */
    Optional<PromoCodeUsage> findByPromoCode_IdAndOrderId(UUID promoCodeId, UUID orderId);

    /**
     * Frees the holds an order had on any code.
     *
     * <p>Scoped to RESERVED on purpose: a REDEEMED row records a discount the customer actually
     * received, and deleting it would hand the code back while the money stayed given away.
     */
    long deleteByOrderIdAndStatus(UUID orderId, PromoCodeUsage.Status status);
}
