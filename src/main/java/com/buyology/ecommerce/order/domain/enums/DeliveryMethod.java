package com.buyology.ecommerce.order.domain.enums;

/**
 * Determines the delivery flow and which actors can update the order.
 *
 * LOCAL_EXPRESS  — fulfilled by a local courier, typically within 30 minutes.
 *                  Courier updates are GPS-tracked in OrderTrackingEvent.
 *
 * INTERNATIONAL  — cross-border shipment managed by the store admin.
 *                  Admin sets the carrier tracking code when marking as SHIPPED.
 */
public enum DeliveryMethod {
    LOCAL_EXPRESS,
    INTERNATIONAL
}
