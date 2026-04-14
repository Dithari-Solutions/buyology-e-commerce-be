package com.buyology.ecommerce.chat.service;

import com.buyology.ecommerce.chat.dto.ChatMessageResponse;
import com.buyology.ecommerce.chat.dto.SendMessageRequest;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface ChatService {

    /**
     * Validates that the customer owns the order and it is in an active state,
     * persists the message, relays it to the courier backend via RabbitMQ, and
     * echoes it back to the sender's WebSocket session.
     *
     * @param ecommerceOrderId  the Order.id (resolved from deliveryOrderId lookup)
     * @param deliveryOrderId   the shared chat-room key
     * @param customerId        authenticated customer UUID
     * @param request           message payload from the STOMP frame
     */
    ChatMessageResponse sendFromCustomer(UUID ecommerceOrderId,
                                         UUID deliveryOrderId,
                                         UUID customerId,
                                         SendMessageRequest request);

    /**
     * Called by the RabbitMQ consumer when a courier message arrives from the
     * courier backend. Persists it and pushes to the customer's WebSocket.
     */
    void receiveFromCourier(UUID deliveryOrderId,
                             UUID ecommerceOrderId,
                             UUID courierId,
                             com.buyology.ecommerce.chat.domain.enums.MessageType messageType,
                             String content,
                             java.time.Instant sentAt,
                             UUID messageId);

    /**
     * Paginated chat history for the customer-side history screen and web chat panel.
     * Requires that the caller is the order owner.
     */
    Page<ChatMessageResponse> getHistory(UUID ecommerceOrderId, UUID customerId, int page, int size);
}
