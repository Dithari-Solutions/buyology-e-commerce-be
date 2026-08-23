package com.buyology.ecommerce.support.dto;

import com.buyology.ecommerce.support.domain.SupportTicketMessage;

import java.time.Instant;

/** One conversation entry on a ticket, as sent to both the storefront and the dashboard. */
public class SupportMessageResponse {

    private String id;
    private String author;   // CUSTOMER | ADMIN
    private String body;
    private Instant createdAt;

    public static SupportMessageResponse from(SupportTicketMessage m) {
        SupportMessageResponse dto = new SupportMessageResponse();
        dto.id = m.getId() == null ? null : m.getId().toString();
        dto.author = m.getAuthor() == null ? null : m.getAuthor().name();
        dto.body = m.getBody();
        dto.createdAt = m.getCreatedAt();
        return dto;
    }

    public String getId() { return id; }
    public String getAuthor() { return author; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
}
