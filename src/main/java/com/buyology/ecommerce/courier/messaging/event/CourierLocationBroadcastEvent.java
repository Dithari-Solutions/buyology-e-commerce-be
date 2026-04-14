package com.buyology.ecommerce.courier.messaging.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Received from {@code buyology.delivery.exchange} with routing key
 * {@code delivery.location.updated} on every GPS ping from the courier.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CourierLocationBroadcastEvent(
        UUID deliveryId,
        UUID ecommerceOrderId,
        UUID courierId,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal heading,
        BigDecimal speed,
        Instant recordedAt
) {}
