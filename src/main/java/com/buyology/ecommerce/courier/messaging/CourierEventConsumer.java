package com.buyology.ecommerce.courier.messaging;

import com.buyology.ecommerce.courier.messaging.event.AssignmentExhaustedEvent;
import com.buyology.ecommerce.courier.messaging.event.CourierAssignedEvent;
import com.buyology.ecommerce.courier.messaging.event.CourierLocationBroadcastEvent;
import com.buyology.ecommerce.courier.messaging.event.DeliveryStatusChangedEvent;
import com.buyology.ecommerce.notification.service.PushNotificationService;
import com.buyology.ecommerce.order.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Consumes all delivery events published by the courier backend to
 * {@code buyology.delivery.exchange} and routes them by routing key.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Sync ecommerce order status from courier delivery status</li>
 *   <li>Send FCM push notifications to customers on key milestones</li>
 *   <li>Broadcast real-time courier location to customer WebSocket subscribers</li>
 * </ul>
 */
@Component
public class CourierEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(CourierEventConsumer.class);

    private final OrderService             orderService;
    private final PushNotificationService  pushService;
    private final SimpMessagingTemplate    messagingTemplate;
    private final ObjectMapper             objectMapper;

    public CourierEventConsumer(OrderService orderService,
                                PushNotificationService pushService,
                                SimpMessagingTemplate messagingTemplate,
                                ObjectMapper objectMapper) {
        this.orderService      = orderService;
        this.pushService       = pushService;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper      = objectMapper;
    }

    @RabbitListener(queues = CourierEventRabbitMQConfig.ECOMMERCE_DELIVERY_EVENTS_QUEUE)
    public void handle(String payload,
                       @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
        log.debug("[CourierEvent] Received routingKey={} payload={}", routingKey, payload);

        try {
            switch (routingKey) {
                case CourierEventRabbitMQConfig.COURIER_ASSIGNED_KEY ->
                        handleCourierAssigned(objectMapper.readValue(payload, CourierAssignedEvent.class));

                case CourierEventRabbitMQConfig.DELIVERY_STATUS_CHANGED_KEY,
                     CourierEventRabbitMQConfig.DELIVERY_COMPLETED_KEY,
                     CourierEventRabbitMQConfig.DELIVERY_CANCELLED_KEY ->
                        handleStatusChanged(objectMapper.readValue(payload, DeliveryStatusChangedEvent.class));

                case CourierEventRabbitMQConfig.ASSIGNMENT_EXHAUSTED_KEY ->
                        handleAssignmentExhausted(objectMapper.readValue(payload, AssignmentExhaustedEvent.class));

                case CourierEventRabbitMQConfig.LOCATION_UPDATED_KEY ->
                        handleLocationUpdate(objectMapper.readValue(payload, CourierLocationBroadcastEvent.class));

                default -> log.debug("[CourierEvent] Ignored unknown routingKey={}", routingKey);
            }
        } catch (Exception ex) {
            log.error("[CourierEvent] Failed to process routingKey={} — {}", routingKey, ex.getMessage(), ex);
            // Re-throw so Spring AMQP routes to DLQ after exhausting retries
            throw new RuntimeException("Failed to process courier event: " + routingKey, ex);
        }
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private void handleCourierAssigned(CourierAssignedEvent event) {
        log.info("[CourierEvent] Courier assigned ecommerceOrderId={} courierId={} name='{}'",
                event.ecommerceOrderId(), event.courierId(), event.courierName());

        orderService.onCourierAssigned(event.ecommerceOrderId(), event.deliveryId(), event.courierId(),
                event.courierName(), event.courierPhone());

        // Push to customer: "Your courier is on the way!"
        orderService.findUserIdByOrderId(event.ecommerceOrderId()).ifPresent(userId ->
                pushService.sendToUser(userId,
                        "Courier assigned!",
                        "Your courier has been assigned and is heading to pick up your order.",
                        Map.of(
                                "type",            "COURIER_ASSIGNED",
                                "orderId",         event.ecommerceOrderId().toString(),
                                "deliveryOrderId", event.deliveryId().toString()
                        ))
        );
    }

    private void handleStatusChanged(DeliveryStatusChangedEvent event) {
        log.info("[CourierEvent] Status changed ecommerceOrderId={} status={} proof={}",
                event.ecommerceOrderId(), event.status(), event.proofImageUrl());

        orderService.syncStatusFromCourier(event.ecommerceOrderId(), event.status(), event.proofImageUrl());

        // Push customer on significant milestones
        orderService.findUserIdByOrderId(event.ecommerceOrderId()).ifPresent(userId -> {
            String title = null;
            String body  = null;
            switch (event.status()) {
                case "PICKED_UP"   -> { title = "Order picked up!";     body = "Your courier has collected your package."; }
                case "ON_THE_WAY"  -> { title = "On the way!";          body = "Your courier is heading to your address."; }
                case "DELIVERED"   -> { title = "Delivered!";           body = "Your order has been delivered. Please rate your experience."; }
                case "FAILED"      -> { title = "Delivery failed";      body = "Unfortunately your delivery could not be completed."; }
                case "CANCELLED"   -> { title = "Order cancelled";      body = "Your delivery has been cancelled."; }
            }
            if (title != null) {
                pushService.sendToUser(userId, title, body,
                        Map.of("type", "ORDER_STATUS_" + event.status(),
                               "orderId", event.ecommerceOrderId().toString()));
            }
        });
    }

    private void handleAssignmentExhausted(AssignmentExhaustedEvent event) {
        log.warn("[CourierEvent] Assignment exhausted ecommerceOrderId={} totalAttempts={}",
                event.ecommerceOrderId(), event.totalAttempts());

        orderService.syncStatusFromCourier(event.ecommerceOrderId(), "FAILED", null);

        orderService.findUserIdByOrderId(event.ecommerceOrderId()).ifPresent(userId ->
                pushService.sendToUser(userId,
                        "Delivery unavailable",
                        "We couldn't find a courier for your order. Our team will follow up.",
                        Map.of("type", "ASSIGNMENT_EXHAUSTED",
                               "orderId", event.ecommerceOrderId().toString()))
        );
    }

    private void handleLocationUpdate(CourierLocationBroadcastEvent event) {
        // Broadcast real-time location to customer WebSocket subscriber
        // Customer subscribes to: /topic/orders/{ecommerceOrderId}/location
        String destination = "/topic/orders/" + event.ecommerceOrderId() + "/location";

        LocationPayload locationPayload = new LocationPayload(
                event.courierId().toString(),
                event.latitude(),
                event.longitude(),
                event.heading(),
                event.speed(),
                event.recordedAt().toString()
        );

        messagingTemplate.convertAndSend(destination, locationPayload);
        log.debug("[CourierEvent] Location broadcast ecommerceOrderId={} lat={} lng={}",
                event.ecommerceOrderId(), event.latitude(), event.longitude());
    }

    // ── Payload DTOs ──────────────────────────────────────────────────────────

    public record LocationPayload(
            String courierId,
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal heading,
            BigDecimal speed,
            String recordedAt
    ) {}
}
