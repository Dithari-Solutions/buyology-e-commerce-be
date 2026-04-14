package com.buyology.ecommerce.chat.service.impl;

import com.buyology.ecommerce.chat.domain.ChatMessage;
import com.buyology.ecommerce.chat.domain.enums.ClientType;
import com.buyology.ecommerce.chat.domain.enums.MessageType;
import com.buyology.ecommerce.chat.domain.enums.SenderType;
import com.buyology.ecommerce.chat.dto.ChatMessageResponse;
import com.buyology.ecommerce.chat.dto.SendMessageRequest;
import com.buyology.ecommerce.chat.messaging.event.ChatMessageEvent;
import com.buyology.ecommerce.chat.repository.ChatMessageRepository;
import com.buyology.ecommerce.chat.service.ChatService;
import com.buyology.ecommerce.order.domain.Order;
import com.buyology.ecommerce.order.domain.enums.OrderStatus;
import com.buyology.ecommerce.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
public class ChatServiceImpl implements ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);

    /** Order statuses where chat is open. */
    private static final Set<OrderStatus> ACTIVE_STATUSES = Set.of(
            OrderStatus.COURIER_ASSIGNED,
            OrderStatus.PICKED_UP,
            OrderStatus.IN_TRANSIT
    );

    /** Order statuses that represent a terminal (closed) state. */
    private static final Set<OrderStatus> TERMINAL_STATUSES = Set.of(
            OrderStatus.DELIVERED,
            OrderStatus.CANCELLED,
            OrderStatus.FAILED
    );

    private static final String DELIVERY_EXCHANGE = "buyology.delivery.exchange";
    private static final String CHAT_FROM_CUSTOMER_KEY = "chat.message.customer";

    private final OrderRepository orderRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final RabbitTemplate rabbitTemplate;

    public ChatServiceImpl(OrderRepository orderRepository,
                           ChatMessageRepository chatMessageRepository,
                           SimpMessagingTemplate messagingTemplate,
                           RabbitTemplate rabbitTemplate) {
        this.orderRepository = orderRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.messagingTemplate = messagingTemplate;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    @Transactional
    public ChatMessageResponse sendFromCustomer(UUID ecommerceOrderId,
                                                UUID deliveryOrderId,
                                                UUID customerId,
                                                SendMessageRequest request) {
        Order order = orderRepository.findById(ecommerceOrderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + ecommerceOrderId));

        // ── Authorization ──────────────────────────────────────────────────────
        if (!order.getUserId().equals(customerId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You do not own this order");
        }

        // ── Active-order guard ─────────────────────────────────────────────────
        if (TERMINAL_STATUSES.contains(order.getStatus())) {
            throw new IllegalStateException(
                    "Chat is closed for order " + ecommerceOrderId + " (status: " + order.getStatus() + ")");
        }
        if (!ACTIVE_STATUSES.contains(order.getStatus())) {
            throw new IllegalStateException(
                    "Chat is not yet available — no courier has been assigned to order " + ecommerceOrderId);
        }

        // ── Web call-type guard ────────────────────────────────────────────────
        if (ClientType.WEB.equals(request.clientType()) && request.messageType() != MessageType.TEXT) {
            throw new IllegalArgumentException(
                    "Call signalling is not supported on web clients. Use messageType=TEXT.");
        }

        // ── Persist ───────────────────────────────────────────────────────────
        ChatMessage msg = new ChatMessage();
        msg.setDeliveryOrderId(deliveryOrderId);
        msg.setEcommerceOrderId(ecommerceOrderId);
        msg.setSenderId(customerId);
        msg.setSenderType(SenderType.CUSTOMER);
        msg.setMessageType(request.messageType());
        msg.setContent(request.content());
        msg.setSentAt(Instant.now());
        chatMessageRepository.save(msg);

        // ── Relay to courier backend via RabbitMQ ─────────────────────────────
        ChatMessageEvent event = new ChatMessageEvent(
                msg.getId(),
                deliveryOrderId,
                ecommerceOrderId,
                customerId,
                SenderType.CUSTOMER,
                request.messageType(),
                request.content(),
                msg.getSentAt()
        );
        rabbitTemplate.convertAndSend(DELIVERY_EXCHANGE, CHAT_FROM_CUSTOMER_KEY, event);
        log.debug("[Chat] Relayed customer message to courier backend deliveryOrderId={}", deliveryOrderId);

        // ── Echo to sender's own WS session (delivery confirmation) ───────────
        ChatMessageResponse response = ChatMessageResponse.from(msg);
        messagingTemplate.convertAndSendToUser(
                customerId.toString(),
                "/queue/chat/" + deliveryOrderId,
                response
        );
        return response;
    }

    @Override
    @Transactional
    public void receiveFromCourier(UUID deliveryOrderId,
                                   UUID ecommerceOrderId,
                                   UUID courierId,
                                   MessageType messageType,
                                   String content,
                                   Instant sentAt,
                                   UUID messageId) {
        Order order = orderRepository.findById(ecommerceOrderId).orElse(null);
        if (order == null) {
            log.warn("[Chat] Received courier message for unknown ecommerceOrderId={}", ecommerceOrderId);
            return;
        }

        // ── Persist ───────────────────────────────────────────────────────────
        ChatMessage msg = new ChatMessage();
        msg.setId(messageId); // preserve the ID assigned by courier backend
        msg.setDeliveryOrderId(deliveryOrderId);
        msg.setEcommerceOrderId(ecommerceOrderId);
        msg.setSenderId(courierId);
        msg.setSenderType(SenderType.COURIER);
        msg.setMessageType(messageType);
        msg.setContent(content);
        msg.setSentAt(sentAt);
        msg.setDeliveredAt(Instant.now());

        if (!chatMessageRepository.existsById(messageId)) {
            chatMessageRepository.save(msg);
        }

        // ── Push to customer WS ───────────────────────────────────────────────
        ChatMessageResponse response = ChatMessageResponse.from(msg);
        messagingTemplate.convertAndSendToUser(
                order.getUserId().toString(),
                "/queue/chat/" + deliveryOrderId,
                response
        );
        log.debug("[Chat] Pushed courier message to customer userId={} deliveryOrderId={}",
                order.getUserId(), deliveryOrderId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ChatMessageResponse> getHistory(UUID ecommerceOrderId, UUID customerId, int page, int size) {
        Order order = orderRepository.findByIdAndUserId(ecommerceOrderId, customerId)
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException(
                        "Order not found or access denied"));

        UUID deliveryOrderId = order.getDeliveryOrderId();
        if (deliveryOrderId == null) {
            return Page.empty(PageRequest.of(page, size));
        }

        return chatMessageRepository
                .findByDeliveryOrderIdOrderBySentAtAsc(deliveryOrderId, PageRequest.of(page, size))
                .map(ChatMessageResponse::from);
    }
}
