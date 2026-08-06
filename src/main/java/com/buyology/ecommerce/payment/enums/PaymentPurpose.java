package com.buyology.ecommerce.payment.enums;

/**
 * What a {@link com.buyology.ecommerce.payment.domain.PaymentTransaction} is paying for.
 *
 * <ul>
 *   <li>{@link #ORDER} — the default: a normal order checkout payment.</li>
 *   <li>{@link #COURIER_RETURN_FEE} — a standalone fee the customer pays to have a
 *       courier pick up a refund return. Not tied to an order; linked to a
 *       refund request and reported as delivery-fee revenue.</li>
 *   <li>{@link #REPAIR_COURIER_FEE} — a standalone fee the customer pays to have a
 *       courier collect/return a device for a repair request. Not tied to an order;
 *       linked to a repair request.</li>
 *   <li>{@link #SELL_COURIER_FEE} — the same standalone fee for a sell (trade-in)
 *       request: collecting the device for valuation, or returning it after the
 *       customer declines the offer. Linked to a sell request.</li>
 * </ul>
 */
public enum PaymentPurpose {
    ORDER,
    COURIER_RETURN_FEE,
    REPAIR_COURIER_FEE,
    SELL_COURIER_FEE
}
