package com.buyology.ecommerce.sell.domain;

/**
 * Lifecycle of a customer sell (trade-in) request — the mirror image of
 * {@link com.buyology.ecommerce.repair.domain.RepairStatus}: instead of quoting what a repair
 * costs, procurement quotes what Buyology will PAY for the device.
 *
 * <pre>
 *   SUBMITTED        request created; customer must choose how the device reaches the store
 *   AWAITING_DEVICE  delivery method chosen (courier pickup / store drop-off); device in transit
 *   UNDER_REVIEW     store received the device; procurement is inspecting and grading it
 *   OFFER_MADE       procurement sent a firm buy-back offer; awaiting the customer's accept/decline
 *   ACCEPTED         customer accepted the offer; awaiting payout at the store
 *   COMPLETED        payout collected — the device is Buyology's, the ticket moves to history
 *   DECLINED         customer declined the offer; the device must be returned (store pickup / courier)
 *   CANCELLED        request closed without a sale
 * </pre>
 */
public enum SellStatus {
    SUBMITTED,
    AWAITING_DEVICE,
    UNDER_REVIEW,
    OFFER_MADE,
    ACCEPTED,
    COMPLETED,
    DECLINED,
    CANCELLED
}
