package com.buyology.ecommerce.assistant.domain;

import com.buyology.ecommerce.assistant.domain.enums.AssistantRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One turn of an assistant conversation.
 *
 * <p>Both sides are stored, so the transcript is a complete record of what customers asked and what
 * the assistant told them — which is the point of persisting at all: the questions people ask a
 * support bot are the gaps in the storefront, and the answers are what the business is on the hook
 * for.
 *
 * <p>{@code inScope} is false on the assistant turns where the model declined an off-topic
 * question. It is stored rather than derived so the refusal rate is measurable: a bot that starts
 * refusing real product questions is a bug you can only see in this column.
 */
@Entity
@Table(name = "assistant_messages", indexes = {
        @Index(name = "idx_assistant_messages_conversation", columnList = "conversation_id, created_at")
})
public class AssistantMessage {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private AssistantConversation conversation;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private AssistantRole role;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** Assistant turns only: false when the question fell outside the company's scope. */
    @Column(name = "in_scope")
    private Boolean inScope;

    /**
     * Products the assistant cited on this turn, newline-separated UUIDs (same encoding as
     * {@code RepairRequest.imageKeys}). Re-read on the next turn so a follow-up like "how much is
     * the second one?" still has those products in context without re-running retrieval.
     */
    @Column(name = "product_ids", columnDefinition = "TEXT")
    private String productIds;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public AssistantMessage() {
    }

    public AssistantMessage(AssistantConversation conversation, AssistantRole role, String content) {
        this.conversation = conversation;
        this.role = role;
        this.content = content;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    /** Decodes {@link #productIds} back into ids, skipping anything unparseable. */
    public List<UUID> citedProductIds() {
        List<UUID> ids = new ArrayList<>();
        if (productIds == null || productIds.isBlank()) {
            return ids;
        }
        for (String raw : productIds.split("\n")) {
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) continue;
            try {
                ids.add(UUID.fromString(trimmed));
            } catch (IllegalArgumentException ignored) {
                // A malformed id is not worth failing a transcript read over.
            }
        }
        return ids;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public AssistantConversation getConversation() { return conversation; }
    public void setConversation(AssistantConversation conversation) { this.conversation = conversation; }

    public AssistantRole getRole() { return role; }
    public void setRole(AssistantRole role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Boolean getInScope() { return inScope; }
    public void setInScope(Boolean inScope) { this.inScope = inScope; }

    public String getProductIds() { return productIds; }
    public void setProductIds(String productIds) { this.productIds = productIds; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
