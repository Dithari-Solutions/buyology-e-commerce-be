package com.buyology.ecommerce.order.dto;

import java.math.BigDecimal;

/**
 * The delivery fees a checkout WILL be charged, in the requested display currency — computed by
 * the same policy the order pipeline uses, so what the checkout page shows is what the order
 * costs. Exists for cart-less flows (Buy Now): the cart preview carries these figures itself.
 */
public record DeliveryQuoteResponse(
        String currency,
        BigDecimal standardFee,
        BigDecimal expressFee,
        BigDecimal freeShippingThreshold,
        boolean qualifiesForFreeShipping) {
}
