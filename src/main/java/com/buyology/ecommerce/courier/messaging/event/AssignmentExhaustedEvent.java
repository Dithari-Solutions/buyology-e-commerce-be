package com.buyology.ecommerce.courier.messaging.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * Received from {@code buyology.delivery.exchange} with routing key
 * {@code delivery.assignment.exhausted} when all courier assignment attempts fail.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AssignmentExhaustedEvent(
        UUID deliveryId,
        UUID ecommerceOrderId,
        int totalAttempts
) {}
