package com.buyology.ecommerce.revenue.dto;

import java.math.BigDecimal;

/** One time-bucket of revenue (e.g. a day, week, month or year). */
public record RevenueBucketRow(
        String period,
        long orders,
        BigDecimal revenue) {
}
