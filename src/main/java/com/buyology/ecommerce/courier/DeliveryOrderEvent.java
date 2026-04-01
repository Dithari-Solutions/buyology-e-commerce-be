package com.buyology.ecommerce.courier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published to {@code buyology.ecommerce.exchange} with routing key
 * {@code order.delivery.requested}.  Field names must match the courier
 * service's {@code DeliveryOrderReceivedEvent} record exactly.
 */
public record DeliveryOrderEvent(
        UUID ecommerceOrderId,
        UUID ecommerceStoreId,

        String customerName,
        String customerPhone,

        String pickupAddress,
        BigDecimal pickupLat,
        BigDecimal pickupLng,

        String dropoffAddress,
        BigDecimal dropoffLat,
        BigDecimal dropoffLng,

        String packageSize,
        BigDecimal packageWeight,
        BigDecimal deliveryFee,

        String priority,

        Instant occurredAt
) {}
