package com.buyology.ecommerce.refund.dto;

/**
 * Storefront-facing view of the refund settings. Exposes only the customer-relevant
 * fields (return/refund window in days and whether returns are enabled) — never the
 * admin id, courier fee, or audit timestamps that {@link RefundSettingResponse} carries.
 */
public record PublicRefundSettingResponse(
        Integer returnWindowDays,
        boolean enabled
) {}
