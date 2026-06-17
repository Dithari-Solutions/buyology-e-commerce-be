package com.buyology.ecommerce.refund.dto;

import com.buyology.ecommerce.payment.dto.PaymentInitiatedResponse;

/**
 * Result of choosing a return method for a refund.
 *
 * For STORE_DROPOFF, {@code payment} is null. For COURIER_PICKUP, the request enters
 * COURIER_FEE_PENDING and {@code payment} carries the Paymob checkout session the
 * customer must complete to pay the courier pickup fee — once it succeeds the request
 * advances to COURIER_REQUESTED.
 */
public record SetReturnMethodResponse(
        RefundRequestResponse refund,
        PaymentInitiatedResponse payment) {
}
