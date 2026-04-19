package com.buyology.ecommerce.courier.messaging.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Published to {@code buyology.ecommerce.exchange} with routing key
 * {@code order.delivery.cancelled} when an EXPRESS order is cancelled in ecommerce.
 * The courier backend consumes this to cancel the in-flight delivery and notify
 * the assigned courier.
 */
public record OrderCancelledEvent(
        UUID ecommerceOrderId,
        String reason,
        Instant cancelledAt
) {}
