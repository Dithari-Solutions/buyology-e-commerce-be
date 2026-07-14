package com.buyology.ecommerce.repair.domain;

/**
 * How a device travels between the customer and the store.
 *
 * Inbound (device → store), chosen right after submission:
 *   COURIER_PICKUP  Buyology sends a courier to collect the device (paid, 20 AED base).
 *   STORE_DROPOFF   customer brings the device to a chosen store branch (free).
 *
 * Return (store → device), chosen only after the customer DECLINES the price:
 *   COURIER_RETURN  Buyology couriers the device back to the customer (paid, 20 AED base).
 *   STORE_PICKUP    customer collects the device from the store branch (free).
 */
public enum RepairDeliveryMethod {
    COURIER_PICKUP,
    STORE_DROPOFF,
    COURIER_RETURN,
    STORE_PICKUP
}
