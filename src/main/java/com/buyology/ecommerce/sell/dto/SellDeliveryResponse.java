package com.buyology.ecommerce.sell.dto;

import com.buyology.ecommerce.payment.dto.PaymentInitiatedResponse;

/**
 * Result of choosing an inbound delivery / return method for a sell request.
 *
 * For the free options (store drop-off / store pickup), {@code payment} is null and
 * {@code sellRequest} already reflects the advanced state. For the courier options the customer
 * must first pay the courier fee: {@code payment} carries the Paymob checkout session to redirect
 * to, and the request only advances once that payment succeeds (via the SellCourierFeePaidEvent
 * webhook).
 */
public record SellDeliveryResponse(
        SellRequestResponse sellRequest,
        PaymentInitiatedResponse payment) {
}
