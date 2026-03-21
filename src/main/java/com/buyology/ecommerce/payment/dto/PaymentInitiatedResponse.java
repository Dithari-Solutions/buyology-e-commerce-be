package com.buyology.ecommerce.payment.dto;

import com.buyology.ecommerce.payment.enums.PaymentMethodType;

import java.math.BigDecimal;
import java.util.UUID;

public class PaymentInitiatedResponse {

    private UUID transactionId;
    private PaymentMethodType methodType;
    private BigDecimal amount;
    private String currency;

    // Single-use token — passed to frontend to open Unified Checkout
    private String clientSecret;

    // Full Unified Checkout URL — open this in a WebView or redirect
    private String checkoutUrl;

    public UUID getTransactionId() { return transactionId; }
    public void setTransactionId(UUID transactionId) { this.transactionId = transactionId; }

    public PaymentMethodType getMethodType() { return methodType; }
    public void setMethodType(PaymentMethodType methodType) { this.methodType = methodType; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }

    public String getCheckoutUrl() { return checkoutUrl; }
    public void setCheckoutUrl(String checkoutUrl) { this.checkoutUrl = checkoutUrl; }
}
