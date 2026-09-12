package com.buyology.ecommerce.order.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * How an order is paid for — which is a different question from which gateway or card was used.
 *
 * <p>The distinction that matters is WHEN the money arrives relative to the goods. Everything the
 * platform did before this enum existed was {@link #ONLINE}: the money is taken first and nothing
 * is picked, packed or dispatched until a payment reaches SUCCESS. {@link #CASH_ON_DELIVERY}
 * inverts that — the goods go out first and the money is handed over at the door or the counter —
 * so it is the one case where an order legitimately moves through fulfilment while still unpaid.
 *
 * <p>Stored per order rather than derived, because it decides things long after checkout: whether
 * fulfilment may start without a payment, whether a cancellation has any money to give back, and
 * what the courier is told to collect.
 */
public enum OrderPaymentMethod {

    /**
     * Paid before fulfilment — card, wallet, B2B credit, bank transfer. The default, and what every
     * order created before this field existed is read as.
     */
    ONLINE,

    /**
     * Paid in cash when the customer receives the goods. The order is fulfilled while unpaid, and
     * an admin records the cash once it is in hand.
     */
    CASH_ON_DELIVERY;

    @JsonCreator
    public static OrderPaymentMethod fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        // "COD" is what the storefront and most of the industry call it.
        if ("COD".equals(normalized) || "CASH".equals(normalized)) {
            return CASH_ON_DELIVERY;
        }
        try {
            return OrderPaymentMethod.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
