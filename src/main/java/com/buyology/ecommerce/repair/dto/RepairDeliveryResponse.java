package com.buyology.ecommerce.repair.dto;

import com.buyology.ecommerce.payment.dto.PaymentInitiatedResponse;

/**
 * Result of choosing an inbound delivery / return method for a repair.
 *
 * For the free options (store drop-off / store pickup), {@code payment} is null and {@code repair}
 * already reflects the advanced state. For the courier options the customer must first pay the
 * courier fee: {@code payment} carries the Paymob checkout session to redirect to, and the repair
 * only advances once that payment succeeds (via the RepairCourierFeePaidEvent webhook).
 */
public record RepairDeliveryResponse(
        RepairRequestResponse repair,
        PaymentInitiatedResponse payment) {
}
