package com.buyology.ecommerce.chat.messaging;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the ecommerce-side queue that receives chat messages relayed from the
 * courier backend (routing key {@code chat.message.courier}).
 *
 * <p>The exchange ({@code buyology.delivery.exchange}) and DLX are already
 * declared by {@link com.buyology.ecommerce.courier.messaging.CourierEventRabbitMQConfig};
 * Spring AMQP is idempotent so re-declaring them here is safe.
 */
@Configuration
public class ChatRabbitMQConfig {

    public static final String DELIVERY_EXCHANGE     = "buyology.delivery.exchange";
    public static final String DELIVERY_DLX          = "buyology.delivery.dlx";
    public static final String DELIVERY_DLQ          = "buyology.delivery.ecommerce.dlq";

    /** Routing key the courier backend publishes courier-originated messages to. */
    public static final String CHAT_FROM_COURIER_KEY = "chat.message.courier";

    /** Queue on which ecommerce consumes messages sent by the courier. */
    public static final String CHAT_FROM_COURIER_QUEUE = "chat.from.courier.queue";

    /** Routing key ecommerce uses to publish customer-originated messages. */
    public static final String CHAT_FROM_CUSTOMER_KEY = "chat.message.customer";

    @Bean
    Queue chatFromCourierQueue() {
        return QueueBuilder.durable(CHAT_FROM_COURIER_QUEUE)
                .withArgument("x-dead-letter-exchange", DELIVERY_DLX)
                .withArgument("x-dead-letter-routing-key", DELIVERY_DLQ)
                .build();
    }

    @Bean
    Binding chatFromCourierBinding(Queue chatFromCourierQueue) {
        TopicExchange exchange = new TopicExchange(DELIVERY_EXCHANGE, true, false);
        return BindingBuilder
                .bind(chatFromCourierQueue)
                .to(exchange)
                .with(CHAT_FROM_COURIER_KEY);
    }
}
