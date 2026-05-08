package com.buyology.ecommerce.membership.dto;

import com.buyology.ecommerce.membership.domain.CreditUsage;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class CreditUsageResponse {

    private UUID id;
    private UUID orderId;
    private BigDecimal amount;
    private String currency;
    private CreditUsage.Status status;
    private Instant usedAt;
    private Instant dueAt;

    public static CreditUsageResponse from(CreditUsage u) {
        CreditUsageResponse r = new CreditUsageResponse();
        r.id = u.getId();
        r.orderId = u.getOrderId();
        r.amount = u.getAmount();
        r.currency = u.getCurrency();
        r.status = u.getStatus();
        r.usedAt = u.getUsedAt();
        r.dueAt = u.getDueAt();
        return r;
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public CreditUsage.Status getStatus() { return status; }
    public Instant getUsedAt() { return usedAt; }
    public Instant getDueAt() { return dueAt; }
}
