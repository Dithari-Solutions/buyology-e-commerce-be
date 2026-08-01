package com.buyology.ecommerce.sell.dto;

import com.buyology.ecommerce.sell.domain.SellStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Procurement's generic status transition (e.g. mark CANCELLED) with an optional note.
 * The customer is emailed with a stage label + note.
 */
public class UpdateSellStatusRequest {

    @NotNull
    private SellStatus status;

    @Size(max = 4000)
    private String note;

    public SellStatus getStatus() { return status; }
    public void setStatus(SellStatus status) { this.status = status; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
