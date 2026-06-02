package com.buyology.ecommerce.revenue.dto;

import java.math.BigDecimal;

/**
 * One time-bucket of revenue (e.g. a day, week, month or year).
 * {@code revenue} is gross; {@code refunded} is the allocated refund total
 * (PAID refunds) for the bucket; {@code net} = revenue − refunded.
 */
public record RevenueBucketRow(
        String period,
        long orders,
        BigDecimal revenue,
        BigDecimal refunded,
        BigDecimal net) {
}
