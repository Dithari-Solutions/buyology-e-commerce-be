package com.buyology.ecommerce.promo.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One redemption of a promo code — who used it, on which order, for how much. */
public record PromoUsageResponse(
        UUID userId,
        String customerName,
        String customerEmail,
        UUID orderId,
        BigDecimal discountApplied,
        Instant usedAt) {
}
