package com.buyology.ecommerce.assistant.repository;

import com.buyology.ecommerce.assistant.domain.AssistantMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AssistantMessageRepository extends JpaRepository<AssistantMessage, UUID> {

    /** Full transcript, oldest first — the order the model must see it in. */
    List<AssistantMessage> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    /**
     * Lightweight (conversationId, content) rows for the customer turns of several conversations at
     * once, oldest first — the admin list column showing what each chat opened with.
     *
     * <p>A projection over one query rather than a transcript load per row: the list would otherwise
     * fetch every message of every conversation on the page just to read the first line of each.
     * Each row is [UUID conversationId, String content].
     */
    @Query("""
            SELECT m.conversation.id, m.content FROM AssistantMessage m
            WHERE m.conversation.id IN :ids
              AND m.role = com.buyology.ecommerce.assistant.domain.enums.AssistantRole.CUSTOMER
            ORDER BY m.createdAt ASC
            """)
    List<Object[]> findCustomerTurnRows(@Param("ids") List<UUID> ids);
}
