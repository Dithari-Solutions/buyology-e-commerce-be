package com.buyology.ecommerce.revenue.dto;

import com.buyology.ecommerce.revenue.enums.RevenuePeriod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A bucketed revenue report for either the platform or a single supplier.
 * {@code scopeLabel} is a human label ("Buyology" or the supplier business name).
 */
public record RevenueReportResponse(
        RevenuePeriod period,
        LocalDate from,
        LocalDate to,
        String scopeLabel,
        BigDecimal totalRevenue,
        BigDecimal totalRefunded,
        BigDecimal netRevenue,
        long totalOrders,
        List<RevenueBucketRow> buckets,
        List<RevenueOrderRow> orders,
        BigDecimal totalDeliveryFeeRevenue) {
}
