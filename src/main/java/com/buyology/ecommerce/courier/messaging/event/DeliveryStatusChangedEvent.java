package com.buyology.ecommerce.courier.messaging.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

/**
 * Received from {@code buyology.delivery.exchange} with routing key
 * {@code delivery.status.changed} or {@code delivery.completed} or
 * {@code delivery.cancelled} on every status transition.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeliveryStatusChangedEvent(
        UUID deliveryId,
        UUID ecommerceOrderId,
        String status,      // matches DeliveryStatus enum name in courier backend
        UUID courierId,
        @com.fasterxml.jackson.annotation.JsonProperty("proofImageUrl") String proofImageUrl,
        String changedBy,
        Instant changedAt
) {}
