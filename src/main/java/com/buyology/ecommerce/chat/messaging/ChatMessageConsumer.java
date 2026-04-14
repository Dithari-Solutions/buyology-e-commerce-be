package com.buyology.ecommerce.chat.messaging;

import com.buyology.ecommerce.chat.domain.enums.MessageType;
import com.buyology.ecommerce.chat.domain.enums.SenderType;
import com.buyology.ecommerce.chat.messaging.event.ChatMessageEvent;
import com.buyology.ecommerce.chat.service.ChatService;
import com.buyology.ecommerce.notification.service.PushNotificationService;
import com.buyology.ecommerce.order.domain.Order;
import com.buyology.ecommerce.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Consumes chat messages sent by couriers from the courier backend via
 * RabbitMQ ({@code chat.from.courier.queue}), then:
 * <ol>
 *   <li>Persists the message in the ecommerce {@code chat_messages} table.</li>
 *   <li>Pushes it to the customer's live WebSocket session.</li>
 *   <li>Falls back to an FCM push notification if the customer is not connected.</li>
 * </ol>
 */
@Component
public class ChatMessageConsumer {

    private static final Logger log = LoggerFactory.getLogger(ChatMessageConsumer.class);

    private final ChatService chatService;
    private final OrderRepository orderRepository;
    private final PushNotificationService pushNotificationService;

    public ChatMessageConsumer(ChatService chatService,
                               OrderRepository orderRepository,
                               PushNotificationService pushNotificationService) {
        this.chatService = chatService;
        this.orderRepository = orderRepository;
        this.pushNotificationService = pushNotificationService;
    }

    @RabbitListener(queues = ChatRabbitMQConfig.CHAT_FROM_COURIER_QUEUE)
    public void onCourierMessage(ChatMessageEvent event) {
        log.info("[Chat] Received courier message deliveryOrderId={} type={}",
                event.deliveryOrderId(), event.messageType());

        // Only TEXT messages need FCM fallback; call signals are WebRTC-only
        chatService.receiveFromCourier(
                event.deliveryOrderId(),
                event.ecommerceOrderId(),
                event.senderId(),
                event.messageType(),
                event.content(),
                event.sentAt(),
                event.messageId()
        );

        // FCM fallback — fires regardless; PushNotificationService is a no-op
        // if the customer has no FCM token or firebase is disabled
        if (event.messageType() == MessageType.TEXT) {
            sendFcmFallback(event);
        }
    }

    private void sendFcmFallback(ChatMessageEvent event) {
        try {
            Order order = orderRepository.findById(event.ecommerceOrderId()).orElse(null);
            if (order == null) return;

            pushNotificationService.sendToUser(
                    order.getUserId(),
                    "New message from your courier",
                    event.content().length() > 80
                            ? event.content().substring(0, 80) + "…"
                            : event.content(),
                    Map.of(
                            "type", "CHAT_MESSAGE",
                            "deliveryOrderId", event.deliveryOrderId().toString(),
                            "ecommerceOrderId", event.ecommerceOrderId().toString(),
                            "senderType", SenderType.COURIER.name()
                    )
            );
        } catch (Exception ex) {
            log.warn("[Chat] FCM fallback failed for deliveryOrderId={} — {}",
                    event.deliveryOrderId(), ex.getMessage());
        }
    }
}
