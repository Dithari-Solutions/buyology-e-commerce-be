package com.buyology.ecommerce.courier.messaging;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the ecommerce-side queue that consumes delivery events published by
 * the courier backend to {@code buyology.delivery.exchange}.
 *
 * <p>The courier backend already declares the exchange and per-routing-key queues
 * on its side. This config binds an additional ecommerce-specific consumer queue
 * so messages are not lost even when this service restarts.
 */
@Configuration
public class CourierEventRabbitMQConfig {

    /** Exchange that the courier backend publishes all delivery events to. */
    public static final String DELIVERY_EXCHANGE = "buyology.delivery.exchange";

    /** Dead-letter exchange for unprocessable messages. */
    public static final String DELIVERY_DLX = "buyology.delivery.dlx";
    public static final String DELIVERY_DLQ = "buyology.delivery.ecommerce.dlq";

    // ── Queue names ───────────────────────────────────────────────────────────

    /** Single fan-out queue for all delivery events — the consumer routes internally. */
    public static final String ECOMMERCE_DELIVERY_EVENTS_QUEUE =
            "buyology.ecommerce.delivery.events.queue";

    // ── Routing keys (must match DeliveryRabbitMQConfig in courier backend) ───
    public static final String COURIER_ASSIGNED_KEY        = "delivery.courier.assigned";
    public static final String ASSIGNMENT_ACCEPTED_KEY     = "delivery.courier.assignment.accepted";
    public static final String DELIVERY_STATUS_CHANGED_KEY = "delivery.status.changed";
    public static final String DELIVERY_COMPLETED_KEY      = "delivery.completed";
    public static final String DELIVERY_CANCELLED_KEY      = "delivery.cancelled";
    public static final String ASSIGNMENT_EXHAUSTED_KEY    = "delivery.assignment.exhausted";
    public static final String LOCATION_UPDATED_KEY        = "delivery.location.updated";

    @Bean
    TopicExchange deliveryExchange() {
        return ExchangeBuilder.topicExchange(DELIVERY_EXCHANGE).durable(true).build();
    }

    @Bean
    DirectExchange deliveryDlx() {
        return ExchangeBuilder.directExchange(DELIVERY_DLX).durable(true).build();
    }

    @Bean
    Queue deliveryDlq() {
        return QueueBuilder.durable(DELIVERY_DLQ).build();
    }

    @Bean
    Binding deliveryDlqBinding() {
        return BindingBuilder.bind(deliveryDlq()).to(deliveryDlx()).with(DELIVERY_DLQ);
    }

    /** Single consumer queue — wildcard binding captures all delivery.# events. */
    @Bean
    Queue ecommerceDeliveryEventsQueue() {
        return QueueBuilder.durable(ECOMMERCE_DELIVERY_EVENTS_QUEUE)
                .withArgument("x-dead-letter-exchange", DELIVERY_DLX)
                .withArgument("x-dead-letter-routing-key", DELIVERY_DLQ)
                .build();
    }

    @Bean
    Binding ecommerceDeliveryEventsBinding() {
        return BindingBuilder
                .bind(ecommerceDeliveryEventsQueue())
                .to(deliveryExchange())
                .with("delivery.#");
    }
}
