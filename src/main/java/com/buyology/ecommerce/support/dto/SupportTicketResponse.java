package com.buyology.ecommerce.support.dto;

import com.buyology.ecommerce.support.domain.SupportTicket;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A support ticket as sent to the storefront and the dashboard. {@code messages} is only
 * populated on detail reads (null on lists to keep them light); {@code imageUrls} are presigned
 * on read from the stored keys.
 */
public class SupportTicketResponse {

    private UUID id;
    private String reference;
    private String category;
    private String subject;
    private String description;
    private String pageUrl;
    private String status;
    private String adminNote;
    private String contactEmail;
    private boolean adminUnread;
    private boolean customerUnread;
    private List<String> imageUrls;
    private List<SupportMessageResponse> messages;
    private Instant resolvedAt;
    private Instant createdAt;
    private Instant updatedAt;

    public static SupportTicketResponse from(SupportTicket t, List<String> imageUrls,
                                             List<SupportMessageResponse> messages) {
        SupportTicketResponse dto = new SupportTicketResponse();
        dto.id = t.getId();
        dto.reference = t.getReference();
        dto.category = t.getCategory() == null ? null : t.getCategory().name();
        dto.subject = t.getSubject();
        dto.description = t.getDescription();
        dto.pageUrl = t.getPageUrl();
        dto.status = t.getStatus() == null ? null : t.getStatus().name();
        dto.adminNote = t.getAdminNote();
        dto.contactEmail = t.getContactEmail();
        dto.adminUnread = t.isAdminUnread();
        dto.customerUnread = t.isCustomerUnread();
        dto.imageUrls = imageUrls;
        dto.messages = messages;
        dto.resolvedAt = t.getResolvedAt();
        dto.createdAt = t.getCreatedAt();
        dto.updatedAt = t.getUpdatedAt();
        return dto;
    }

    public UUID getId() { return id; }
    public String getReference() { return reference; }
    public String getCategory() { return category; }
    public String getSubject() { return subject; }
    public String getDescription() { return description; }
    public String getPageUrl() { return pageUrl; }
    public String getStatus() { return status; }
    public String getAdminNote() { return adminNote; }
    public String getContactEmail() { return contactEmail; }
    public boolean isAdminUnread() { return adminUnread; }
    public boolean isCustomerUnread() { return customerUnread; }
    public List<String> getImageUrls() { return imageUrls; }
    public List<SupportMessageResponse> getMessages() { return messages; }
    public Instant getResolvedAt() { return resolvedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
