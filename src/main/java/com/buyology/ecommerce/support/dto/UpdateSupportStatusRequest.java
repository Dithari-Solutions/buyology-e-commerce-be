package com.buyology.ecommerce.support.dto;

import com.buyology.ecommerce.support.domain.SupportTicketStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Generic team status transition with an optional note shown to the customer. */
public class UpdateSupportStatusRequest {

    @NotNull(message = "A status is required.")
    private SupportTicketStatus status;

    @Size(max = 2000, message = "Notes are limited to 2000 characters.")
    private String note;

    public SupportTicketStatus getStatus() { return status; }
    public void setStatus(SupportTicketStatus status) { this.status = status; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
