package com.buyology.ecommerce.courier;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the RabbitMQ exchange topology shared with the courier service.
 * Must match DeliveryRabbitMQConfig in buyology-courier exactly.
 */
@Configuration
public class DeliveryRabbitMQConfig {

    public static final String ECOMMERCE_EXCHANGE          = "buyology.ecommerce.exchange";
    public static final String ORDER_DELIVERY_REQUESTED_KEY = "order.delivery.requested";

    @Bean
    TopicExchange ecommerceExchange() {
        return ExchangeBuilder.topicExchange(ECOMMERCE_EXCHANGE).durable(true).build();
    }

    @Bean
    JacksonJsonMessageConverter jackson2JsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                  JacksonJsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }
}
