package com.buyology.ecommerce.chat.controller;

import com.buyology.ecommerce.chat.dto.SendMessageRequest;
import com.buyology.ecommerce.chat.service.ChatService;
import com.buyology.ecommerce.order.repository.OrderRepository;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;


/**
 * Handles inbound STOMP messages from the customer.
 *
 * <h3>Client sends to:</h3>
 * {@code /app/chat/{deliveryOrderId}/send}
 *
 * <h3>Client receives on:</h3>
 * {@code /user/queue/chat/{deliveryOrderId}}
 *
 * <p>The {@code deliveryOrderId} is the shared chat-room key (matches
 * {@code DeliveryOrder.id} in the courier backend and {@code Order.deliveryOrderId} here).
 * The customer must first look up the {@code deliveryOrderId} via the order REST API.
 *
 * <p>Authentication: the STOMP CONNECT frame must carry a valid customer JWT in the
 * {@code Authorization: Bearer <token>} native header.
 */
@Controller
public class ChatWebSocketController {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketController.class);

    private final ChatService chatService;
    private final OrderRepository orderRepository;

    public ChatWebSocketController(ChatService chatService, OrderRepository orderRepository) {
        this.chatService = chatService;
        this.orderRepository = orderRepository;
    }

    /**
     * Receives a message from the customer and relays it to the assigned courier.
     *
     * @param deliveryOrderId the chat-room key (Order.deliveryOrderId)
     * @param request         the message payload
     * @param principal       the authenticated customer (set by WebSocketConfig JWT interceptor)
     */
    @MessageMapping("/chat/{deliveryOrderId}/send")
    public void send(@DestinationVariable UUID deliveryOrderId,
                     @Valid @Payload SendMessageRequest request,
                     Principal principal) {

        UUID customerId = UUID.fromString(principal.getName());

        // Resolve ecommerceOrderId from deliveryOrderId (indexed lookup)
        UUID ecommerceOrderId = orderRepository
                .findByDeliveryOrderId(deliveryOrderId)
                .map(o -> o.getId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No active order found for deliveryOrderId=" + deliveryOrderId));

        log.debug("[Chat-WS] Customer {} → deliveryOrderId={} type={}",
                customerId, deliveryOrderId, request.messageType());

        chatService.sendFromCustomer(ecommerceOrderId, deliveryOrderId, customerId, request);
    }
}
