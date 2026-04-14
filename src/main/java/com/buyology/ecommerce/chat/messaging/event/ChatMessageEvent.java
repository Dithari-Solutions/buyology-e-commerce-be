package com.buyology.ecommerce.chat.messaging.event;

import com.buyology.ecommerce.chat.domain.enums.MessageType;
import com.buyology.ecommerce.chat.domain.enums.SenderType;

import java.time.Instant;
import java.util.UUID;

/**
 * RabbitMQ event published when either party sends a chat message.
 *
 * <ul>
 *   <li>Ecommerce → Courier  routing key: {@code chat.message.customer}</li>
 *   <li>Courier → Ecommerce  routing key: {@code chat.message.courier}</li>
 * </ul>
 */
public record ChatMessageEvent(
        UUID messageId,
        UUID deliveryOrderId,
        UUID ecommerceOrderId,
        UUID senderId,
        SenderType senderType,
        MessageType messageType,
        String content,
        Instant sentAt
) {}
