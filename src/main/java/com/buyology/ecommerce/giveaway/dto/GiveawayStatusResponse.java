package com.buyology.ecommerce.giveaway.dto;

import com.buyology.ecommerce.giveaway.domain.GiveawayEntry;

import java.time.Instant;

/**
 * Whether the caller is in the giveaway, plus what the entry form needs to know before it
 * lets them try: {@code eligible} is false when the account cannot enter yet (today: an
 * unverified phone number), with {@code reason} naming the gate so the UI can send them to
 * the right place instead of failing the submit.
 */
public class GiveawayStatusResponse {

    private boolean entered;
    private String instagramHandle;
    private Instant enteredAt;
    private boolean eligible;
    private String reason;
    private long totalEntries;

    public static GiveawayStatusResponse notEntered(boolean eligible, String reason, long totalEntries) {
        GiveawayStatusResponse dto = new GiveawayStatusResponse();
        dto.entered = false;
        dto.eligible = eligible;
        dto.reason = reason;
        dto.totalEntries = totalEntries;
        return dto;
    }

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
    public String getReason() { return reason; }
    public long getTotalEntries() { return totalEntries; }
}
