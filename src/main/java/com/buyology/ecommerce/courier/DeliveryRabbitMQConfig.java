package com.buyology.ecommerce.courier;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the RabbitMQ exchange topology shared with the courier service.
 * Must match DeliveryRabbitMQConfig in buyology-courier exactly.
 * Spring Boot auto-configures RabbitTemplate + JSON message converter via
 * spring-boot-starter-amqp when Jackson is on the classpath.
 */
@Configuration
public class DeliveryRabbitMQConfig {

    public static final String ECOMMERCE_EXCHANGE          = "buyology.ecommerce.exchange";
    public static final String ORDER_DELIVERY_REQUESTED_KEY = "order.delivery.requested";

    @Bean
    TopicExchange ecommerceExchange() {
        return ExchangeBuilder.topicExchange(ECOMMERCE_EXCHANGE).durable(true).build();
    }
}
