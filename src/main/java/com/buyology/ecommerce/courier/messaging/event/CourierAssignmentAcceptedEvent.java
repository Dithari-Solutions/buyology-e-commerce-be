package com.buyology.ecommerce.courier.messaging.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

/**
 * Received from {@code buyology.delivery.exchange} with routing key
 * {@code delivery.courier.assignment.accepted} when a courier accepts their assignment.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CourierAssignmentAcceptedEvent(
        UUID deliveryId,
        UUID ecommerceOrderId,
        UUID courierId,
        String courierName,
        String courierPhone,
        UUID assignmentId,
        Instant acceptedAt
) {}
