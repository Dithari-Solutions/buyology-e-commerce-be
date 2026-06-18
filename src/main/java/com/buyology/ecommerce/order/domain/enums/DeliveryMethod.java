package com.buyology.ecommerce.order.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Determines the delivery flow and which actors can update the order.
 *
 * EXPRESS  — fulfilled by a local courier, typically within 30 minutes.
 *                     Courier updates are GPS-tracked in OrderTrackingEvent.
 *
 * REGULAR     — standard local delivery for distances > 30 minutes within the same country.
 *
 * PICKUP      — customer collects the order from a chosen store branch; no delivery address,
 *               no shipping fee.
 */
public enum DeliveryMethod {
    EXPRESS,
    REGULAR,
    INTERNATIONAL,
    PICKUP;

    @JsonCreator
    public static DeliveryMethod fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        
        // Handle legacy names
        if ("EXPRESS_DELIVERY".equals(normalized) || "LOCAL_EXPRESS".equals(normalized)) {
            return EXPRESS;
        }
        if ("REGULAR_ORDER".equals(normalized)) {
            return REGULAR;
        }
        
        try {
            return DeliveryMethod.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
