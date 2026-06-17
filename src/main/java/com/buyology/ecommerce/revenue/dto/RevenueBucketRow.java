package com.buyology.ecommerce.revenue.dto;

import java.math.BigDecimal;

/**
 * One time-bucket of revenue (e.g. a day, week, month or year).
 * {@code revenue} is gross product revenue; {@code refunded} is the allocated refund
 * total (PAID refunds) for the bucket; {@code net} = revenue − refunded.
 * {@code deliveryFeeRevenue} is the courier return-pickup fees collected in the bucket
 * (settlement currency, AED) — reported separately, not folded into {@code net}.
 */
public record RevenueBucketRow(
        String period,
        long orders,
        BigDecimal revenue,
        BigDecimal refunded,
        BigDecimal net,
        BigDecimal deliveryFeeRevenue) {
}
