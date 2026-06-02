package com.buyology.ecommerce.revenue.dto;

import com.buyology.ecommerce.revenue.enums.RevenuePeriod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** All-suppliers revenue overview for the admin "Supplier Revenues" page. */
public record SupplierRevenueOverviewResponse(
        RevenuePeriod period,
        LocalDate from,
        LocalDate to,
        BigDecimal totalRevenue,
        long totalOrders,
        List<SupplierRevenueRow> suppliers) {
}
