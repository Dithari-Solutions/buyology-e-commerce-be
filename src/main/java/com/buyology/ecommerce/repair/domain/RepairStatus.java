package com.buyology.ecommerce.repair.domain;

/**
 * Lifecycle of a customer device-repair request.
 *
 * <pre>
 *   SUBMITTED        request created; customer must choose how the device reaches the store
 *   AWAITING_DEVICE  delivery method chosen (courier pickup / store drop-off); device in transit
 *   UNDER_REVIEW     store received the device; the repair team is diagnosing it
 *   PRICE_ESTIMATED  team quoted a fixing price; awaiting the customer's accept/decline
 *   IN_REPAIR        customer accepted the price; the repair is in progress ("Fix Started")
 *   COMPLETED        repair finished; ticket moves to history
 *   DECLINED         customer declined the price; device must be returned (store pickup / courier)
 *   CANCELLED        request closed without repair
 * </pre>
 */
public enum RepairStatus {
    SUBMITTED,
    AWAITING_DEVICE,
    UNDER_REVIEW,
    PRICE_ESTIMATED,
    IN_REPAIR,
    COMPLETED,
    DECLINED,
    CANCELLED
}
