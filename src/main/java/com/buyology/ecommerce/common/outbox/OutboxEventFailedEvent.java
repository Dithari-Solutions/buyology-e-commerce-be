package com.buyology.ecommerce.common.outbox;

import java.util.UUID;

/**
 * Published via Spring's ApplicationEventPublisher when an outbox event exhausts
 * its retry budget and is permanently marked {@link OutboxStatus#FAILED}.
 *
 * <p>Listeners can hook alerting, paging or metrics onto this event. The outbox
 * entity has no dedicated "aggregate id" column, so the event id doubles as the
 * stable identifier for manual replay.</p>
 *
 * @param eventId      the outbox event id (used as aggregate/replay identifier)
 * @param eventType    the routing key the event was destined for
 * @param exchange     the target exchange
 * @param retryCount   the number of publish attempts made before giving up
 * @param errorMessage the last failure reason, if any
 */
public record OutboxEventFailedEvent(
        UUID eventId,
        String eventType,
        String exchange,
        int retryCount,
        String errorMessage) {
}
