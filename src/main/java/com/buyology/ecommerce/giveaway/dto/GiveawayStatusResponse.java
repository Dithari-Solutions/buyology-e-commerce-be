package com.buyology.ecommerce.giveaway.dto;

import com.buyology.ecommerce.giveaway.domain.GiveawayEntry;

import java.time.Instant;
import java.util.List;

/**
 * Whether the caller is in the giveaway, plus what the entry form needs to know before it
 * lets them try: {@code eligible} is false while the account still lacks something a prize
 * delivery needs, and {@code missing} names each gap ("phoneNumber", "phoneVerification",
 * "deliveryAddress") so the UI can send them to the right place instead of failing the submit.
 */
public class GiveawayStatusResponse {

    private boolean entered;
    private String instagramHandle;
    private Instant enteredAt;
    private boolean eligible;
    /** Field names still to fill: phoneNumber, phoneVerification, deliveryAddress. */
    private List<String> missing = List.of();
    private long totalEntries;
    /** False once an admin has closed the campaign — every entry surface hides on this. */
    private boolean open = true;

    public static GiveawayStatusResponse notEntered(List<String> missing, long totalEntries) {
        GiveawayStatusResponse dto = new GiveawayStatusResponse();
        dto.entered = false;
        dto.eligible = missing.isEmpty();
        dto.missing = missing;
        dto.totalEntries = totalEntries;
        return dto;
    }

    public boolean isOpen() { return open; }
    public void setOpen(boolean open) { this.open = open; }
    public void setEligible(boolean eligible) { this.eligible = eligible; }

    public static GiveawayStatusResponse from(GiveawayEntry entry, long totalEntries) {
        GiveawayStatusResponse dto = new GiveawayStatusResponse();
        dto.entered = true;
        dto.instagramHandle = entry.getInstagramHandle();
        dto.enteredAt = entry.getCreatedAt();
        dto.eligible = true;
        dto.totalEntries = totalEntries;
        return dto;
    }

    public boolean isEntered() { return entered; }
    public String getInstagramHandle() { return instagramHandle; }
    public Instant getEnteredAt() { return enteredAt; }
    public boolean isEligible() { return eligible; }
    public List<String> getMissing() { return missing; }
    public long getTotalEntries() { return totalEntries; }
}
