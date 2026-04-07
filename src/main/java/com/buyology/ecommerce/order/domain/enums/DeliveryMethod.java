package com.buyology.ecommerce.order.domain.enums;

/**
 * Determines the delivery flow and which actors can update the order.
 *
 * EXPRESS  — fulfilled by a local courier, typically within 30 minutes.
 *                     Courier updates are GPS-tracked in OrderTrackingEvent.
 *
 * REGULAR     — standard local delivery for distances > 30 minutes within the same country.
 */
public enum DeliveryMethod {
    EXPRESS,
    REGULAR,
    INTERNATIONAL
}
