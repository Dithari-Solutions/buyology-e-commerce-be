package com.buyology.ecommerce.courier;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the RabbitMQ exchange topology shared with the courier service.
 * Must match DeliveryRabbitMQConfig in buyology-courier exactly.
 */
@Configuration
public class DeliveryRabbitMQConfig {

    public static final String ECOMMERCE_EXCHANGE             = "buyology.ecommerce.exchange";
    public static final String DELIVERY_ORDER_RECEIVED_QUEUE = "delivery.order.received.queue";
    public static final String ORDER_DELIVERY_REQUESTED_KEY  = "order.delivery.requested";
    public static final String ORDER_DELIVERY_CANCELLED_KEY  = "order.delivery.cancelled";

    @Bean
    TopicExchange ecommerceExchange() {
        return ExchangeBuilder.topicExchange(ECOMMERCE_EXCHANGE).durable(true).build();
    }

    @Bean
    Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                  Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        // Required so the outbox publisher can block on broker ACKs
        // (RabbitTemplate#invoke + waitForConfirmsOrDie). Has no effect unless the
        // underlying connection factory is in CONFIRMING/correlated mode, which is
        // enabled via spring.rabbitmq.publisher-confirm-type=correlated.
        template.setMandatory(true);
        return template;
    }
}
