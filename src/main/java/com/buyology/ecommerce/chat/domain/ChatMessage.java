package com.buyology.ecommerce.chat.domain;

import com.buyology.ecommerce.chat.domain.enums.MessageType;
import com.buyology.ecommerce.chat.domain.enums.SenderType;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Persisted chat message between a customer and the assigned courier for an
 * active EXPRESS delivery.
 *
 * <p>Both sides of the conversation (CUSTOMER and COURIER messages) are stored
 * in this table so the customer can view a complete history via
 * {@code GET /api/orders/{orderId}/chat}.
 *
 * <p>The {@code deliveryOrderId} is the cross-service chat-room key — it matches
 * {@code DeliveryOrder.id} in the courier backend and {@code Order.deliveryOrderId}
 * here, enabling both services to reference the same conversation.
 */
@Entity
@Table(name = "chat_messages", indexes = {
        @Index(name = "idx_chat_messages_delivery_order", columnList = "delivery_order_id, sent_at DESC")
})
public class ChatMessage {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Cross-service chat room key — matches courier backend's DeliveryOrder.id. */
    @Column(name = "delivery_order_id", nullable = false)
    private UUID deliveryOrderId;

    /** Ecommerce Order.id — used to authorise customer access and lookup. */
    @Column(name = "ecommerce_order_id", nullable = false)
    private UUID ecommerceOrderId;

    /** UUID of the customer (Order.userId) or courier (Order.courierUserId). */
    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_type", nullable = false, length = 20)
    private SenderType senderType;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 30)
    private MessageType messageType;

    /** Plain text for TEXT messages; JSON-encoded SDP / ICE candidate for call signals. */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    /** Set by the receiving service when the message is relayed to the recipient's WS. */
    @Column(name = "delivered_at")
    private Instant deliveredAt;

    /** Set when the recipient explicitly acknowledges the message (optional read receipts). */
    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        if (this.sentAt == null) this.sentAt = now;
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getDeliveryOrderId() { return deliveryOrderId; }
    public void setDeliveryOrderId(UUID deliveryOrderId) { this.deliveryOrderId = deliveryOrderId; }

    public UUID getEcommerceOrderId() { return ecommerceOrderId; }
    public void setEcommerceOrderId(UUID ecommerceOrderId) { this.ecommerceOrderId = ecommerceOrderId; }

    public UUID getSenderId() { return senderId; }
    public void setSenderId(UUID senderId) { this.senderId = senderId; }

    public SenderType getSenderType() { return senderType; }
    public void setSenderType(SenderType senderType) { this.senderType = senderType; }

    public MessageType getMessageType() { return messageType; }
    public void setMessageType(MessageType messageType) { this.messageType = messageType; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }

    public Instant getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(Instant deliveredAt) { this.deliveredAt = deliveredAt; }

    public Instant getReadAt() { return readAt; }
    public void setReadAt(Instant readAt) { this.readAt = readAt; }

    public Instant getCreatedAt() { return createdAt; }
}
