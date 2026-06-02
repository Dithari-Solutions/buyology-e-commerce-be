package com.buyology.ecommerce.revenue.dto;

import java.math.BigDecimal;

/**
 * One order's contribution to a revenue scope (platform or a single supplier):
 * {@code gross} is the scope's item total on that order, {@code refunded} its
 * allocated share of the order's PAID refunds, {@code net} = gross − refunded.
 */
public record RevenueOrderRow(
        String orderId,
        String createdAt,
        BigDecimal gross,
        BigDecimal refunded,
        BigDecimal net) {
}
