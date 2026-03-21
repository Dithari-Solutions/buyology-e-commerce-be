package com.buyology.ecommerce.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public class RefundRequest {

    @NotNull
    private UUID transactionId;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal amount;

    private String reason;

    private String notes;

    // UUID of the admin initiating the refund; use a system UUID for automated refunds
    @NotNull
    private UUID refundedBy;

    public UUID getTransactionId() { return transactionId; }
    public void setTransactionId(UUID transactionId) { this.transactionId = transactionId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public UUID getRefundedBy() { return refundedBy; }
    public void setRefundedBy(UUID refundedBy) { this.refundedBy = refundedBy; }
}
