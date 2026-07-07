package com.buyology.ecommerce.b2b.productrequest.dto;

import com.buyology.ecommerce.b2b.productrequest.domain.B2bProductRequestStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Procurement advances a request's lifecycle, with an optional note emailed to the member. */
public class UpdateProductRequestStatusRequest {

    @NotNull
    private B2bProductRequestStatus status;

    @Size(max = 4000)
    private String note;

    public B2bProductRequestStatus getStatus() { return status; }
    public void setStatus(B2bProductRequestStatus status) { this.status = status; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
