package com.buyology.ecommerce.courier.messaging.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

/**
 * Received from {@code buyology.delivery.exchange} with routing key
 * {@code delivery.courier.assigned} when the courier backend assigns a courier.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CourierAssignedEvent(
        UUID deliveryId,
        UUID ecommerceOrderId,
        UUID courierId,
        UUID assignmentId,
        int attemptNumber,
        Instant assignedAt
) {}
