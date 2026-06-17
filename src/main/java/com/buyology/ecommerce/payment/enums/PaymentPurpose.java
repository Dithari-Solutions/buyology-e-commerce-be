package com.buyology.ecommerce.payment.enums;

/**
 * What a {@link com.buyology.ecommerce.payment.domain.PaymentTransaction} is paying for.
 *
 * <ul>
 *   <li>{@link #ORDER} — the default: a normal order checkout payment.</li>
 *   <li>{@link #COURIER_RETURN_FEE} — a standalone fee the customer pays to have a
 *       courier pick up a refund return. Not tied to an order; linked to a
 *       refund request and reported as delivery-fee revenue.</li>
 * </ul>
 */
public enum PaymentPurpose {
    ORDER,
    COURIER_RETURN_FEE
}
