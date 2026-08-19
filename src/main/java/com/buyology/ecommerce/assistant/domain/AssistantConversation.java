package com.buyology.ecommerce.assistant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One storefront conversation with the AI assistant.
 *
 * <p>The conversation id is the only handle the widget holds; the transcript itself lives here and
 * is replayed from the database on every turn. The client never sends history back, so it cannot
 * forge an assistant turn (the classic way to talk a scoped bot out of its scope) and cannot grow
 * the prompt at will.
 *
 * <p>Anonymous by design — the visitors most likely to ask "do you have X in stock?" are not logged
 * in. {@code userId} is filled in only when a signed-in customer happens to be chatting, purely so
 * support can tie a transcript to an account; nothing in the flow requires it.
 *
 * <p>{@code messageCount} counts customer turns and is the per-conversation abuse ceiling. It is a
 * column rather than a COUNT(*) because it is checked on the hot path of every message.
 */
@Entity
@Table(name = "assistant_conversations", indexes = {
        @Index(name = "idx_assistant_conversations_last_message", columnList = "last_message_at DESC"),
        @Index(name = "idx_assistant_conversations_visitor", columnList = "visitor_id")
})
public class AssistantConversation {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Opaque browser id from the storefront (same id the visitor beacon uses). May be null. */
    @Column(name = "visitor_id", length = 64)
    private String visitorId;

    /** Set only when the chatting customer is signed in. Null for anonymous visitors. */
    @Column(name = "user_id")
    private UUID userId;

    /** Reply language, e.g. "en", "ar". */
    @Column(name = "language", length = 8)
    private String language;

    /** ISO-3166 alpha-2 — scopes catalog lookups to what is actually sold there. */
    @Column(name = "country_code", length = 2)
    private String countryCode;

    /** Display currency for any price the assistant quotes. */
    @Column(name = "currency", length = 8)
    private String currency;

    /** Customer turns so far. Compared against app.assistant.max-messages-per-conversation. */
    @Column(name = "message_count", nullable = false)
    private Integer messageCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_message_at", nullable = false)
    private Instant lastMessageAt;

    public AssistantConversation() {
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        if (this.lastMessageAt == null) {
            this.lastMessageAt = now;
        }
        if (this.messageCount == null) {
            this.messageCount = 0;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.lastMessageAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getVisitorId() { return visitorId; }
    public void setVisitorId(String visitorId) { this.visitorId = visitorId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public Integer getMessageCount() { return messageCount; }
    public void setMessageCount(Integer messageCount) { this.messageCount = messageCount; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getLastMessageAt() { return lastMessageAt; }
    public void setLastMessageAt(Instant lastMessageAt) { this.lastMessageAt = lastMessageAt; }
}
