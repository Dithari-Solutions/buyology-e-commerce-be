package com.buyology.ecommerce.sell.domain;

/**
 * How a device travels between the customer and the store for a sell request. Identical in shape
 * to {@link com.buyology.ecommerce.repair.domain.RepairDeliveryMethod} — the sell flow deliberately
 * reuses the repair delivery experience.
 *
 * Inbound (device → store), chosen right after submission:
 *   COURIER_PICKUP  Buyology sends a courier to collect the device (paid, 20 AED base).
 *   STORE_DROPOFF   customer brings the device to a chosen store branch (free).
 *
 * Return (store → customer), chosen only after the customer DECLINES the offer:
 *   COURIER_RETURN  Buyology couriers the device back to the customer (paid, 20 AED base).
 *   STORE_PICKUP    customer collects the device from the store branch (free).
 */
public enum SellDeliveryMethod {
    COURIER_PICKUP,
    STORE_DROPOFF,
    COURIER_RETURN,
    STORE_PICKUP
}
