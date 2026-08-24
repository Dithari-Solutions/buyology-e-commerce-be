package com.buyology.ecommerce.giveaway.dto;

import com.buyology.ecommerce.giveaway.domain.GiveawayEntry;

import java.time.Instant;
import java.util.UUID;

/** An entry as the team sees it when drawing a winner. */
public class GiveawayEntryAdminResponse {

    private UUID id;
    private UUID userId;
    private String instagramHandle;
    private String instagramHandleRaw;
    private String contactEmail;
    private String contactPhone;
    private Instant createdAt;

    public static GiveawayEntryAdminResponse from(GiveawayEntry e) {
        GiveawayEntryAdminResponse dto = new GiveawayEntryAdminResponse();
        dto.id = e.getId();
        dto.userId = e.getUserId();
        dto.instagramHandle = e.getInstagramHandle();
        dto.instagramHandleRaw = e.getInstagramHandleRaw();
        dto.contactEmail = e.getContactEmail();
        dto.contactPhone = e.getContactPhone();
        dto.createdAt = e.getCreatedAt();
        return dto;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getInstagramHandle() { return instagramHandle; }
    public String getInstagramHandleRaw() { return instagramHandleRaw; }
    public String getContactEmail() { return contactEmail; }
    public String getContactPhone() { return contactPhone; }
    public Instant getCreatedAt() { return createdAt; }
}
