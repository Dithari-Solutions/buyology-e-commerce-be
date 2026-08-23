package com.buyology.ecommerce.support.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * One entry in a support ticket's conversation — a customer reply or a team reply/note.
 * Loose ticket reference by id (no JPA relation), matching how the rest of the codebase links
 * aggregates. Column names mirror V42 (support_ticket_messages) exactly.
 */
@Entity
@Table(name = "support_ticket_messages", indexes = {
        @Index(name = "idx_support_ticket_messages_ticket", columnList = "ticket_id, created_at")
})
public class SupportTicketMessage {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Enumerated(EnumType.STRING)
    @Column(name = "author", nullable = false, length = 10)
    private SupportMessageAuthor author;

    /** users.id of the writer — the customer's or the admin's. */
    @Column(name = "author_user_id")
    private UUID authorUserId;

    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getTicketId() { return ticketId; }
    public void setTicketId(UUID ticketId) { this.ticketId = ticketId; }
    public SupportMessageAuthor getAuthor() { return author; }
    public void setAuthor(SupportMessageAuthor author) { this.author = author; }
    public UUID getAuthorUserId() { return authorUserId; }
    public void setAuthorUserId(UUID authorUserId) { this.authorUserId = authorUserId; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public Instant getCreatedAt() { return createdAt; }
}
