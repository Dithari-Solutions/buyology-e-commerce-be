package com.buyology.ecommerce.membership.dto;

import jakarta.validation.constraints.Size;

public class ApplicationActionRequest {

    private String action; // APPROVE, REJECT, UNDER_REVIEW

    @Size(max = 2000)
    private String rejectionReason;

    private String performedBy;

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public String getPerformedBy() { return performedBy; }
    public void setPerformedBy(String performedBy) { this.performedBy = performedBy; }
}
