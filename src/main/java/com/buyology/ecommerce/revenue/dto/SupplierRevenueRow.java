package com.buyology.ecommerce.revenue.dto;

import java.math.BigDecimal;
import java.util.UUID;

/** A single supplier's revenue total in the all-suppliers overview. */
public record SupplierRevenueRow(
        UUID supplierId,
        String businessName,
        long orders,
        BigDecimal revenue) {
}
