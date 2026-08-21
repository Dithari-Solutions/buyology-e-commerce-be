package com.buyology.ecommerce.assistant.repository;

import com.buyology.ecommerce.assistant.domain.AssistantConversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface AssistantConversationRepository extends JpaRepository<AssistantConversation, UUID> {

    /** Admin transcript listing — newest conversation first. */
    Page<AssistantConversation> findAllByOrderByLastMessageAtDesc(Pageable pageable);

    /**
     * Per-visitor daily ceiling. The rate limiter caps requests per minute per IP; this caps how
     * much one browser can spend in a day, which is the limit that actually bounds the API bill
     * when someone sits on the widget all afternoon.
     */
    long countByVisitorIdAndCreatedAtAfter(String visitorId, Instant since);
}
