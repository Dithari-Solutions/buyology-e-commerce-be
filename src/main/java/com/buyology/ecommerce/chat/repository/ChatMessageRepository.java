package com.buyology.ecommerce.chat.repository;

import com.buyology.ecommerce.chat.domain.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    /** Full chronological history for a delivery chat room. */
    Page<ChatMessage> findByDeliveryOrderIdOrderBySentAtAsc(UUID deliveryOrderId, Pageable pageable);
}
