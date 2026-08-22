package com.buyology.ecommerce.payment.dto;

import com.buyology.ecommerce.payment.enums.PaymentMethodType;
import com.buyology.ecommerce.payment.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class TransactionResponse {

    private UUID id;
    private UUID appOrderId;
    private PaymentMethodType methodType;
    private BigDecimal amount;
    private Long amountCents;
    private String currency;
    private PaymentStatus status;
    private String paymobTransactionId;
    private String failureReason;
    private String failureCode;
    /** Last four digits of the paying card, when Paymob reported them. */
    private String cardLast4;
    /** Card brand (e.g. "MasterCard"), when Paymob reported it. */
    private String cardBrand;
    private Instant createdAt;
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getAppOrderId() { return appOrderId; }
    public void setAppOrderId(UUID appOrderId) { this.appOrderId = appOrderId; }

    public PaymentMethodType getMethodType() { return methodType; }
    public void setMethodType(PaymentMethodType methodType) { this.methodType = methodType; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public Long getAmountCents() { return amountCents; }
    public void setAmountCents(Long amountCents) { this.amountCents = amountCents; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }

    public String getPaymobTransactionId() { return paymobTransactionId; }
    public void setPaymobTransactionId(String paymobTransactionId) { this.paymobTransactionId = paymobTransactionId; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public String getFailureCode() { return failureCode; }
    public void setFailureCode(String failureCode) { this.failureCode = failureCode; }
    public String getCardLast4() { return cardLast4; }
    public void setCardLast4(String cardLast4) { this.cardLast4 = cardLast4; }
    public String getCardBrand() { return cardBrand; }
    public void setCardBrand(String cardBrand) { this.cardBrand = cardBrand; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
