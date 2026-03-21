package com.buyology.ecommerce.payment.dto;

import com.buyology.ecommerce.payment.enums.PaymentMethodType;

import java.math.BigDecimal;
import java.util.UUID;

public class PaymentInitiatedResponse {

    private UUID transactionId;
    private PaymentMethodType methodType;
    private BigDecimal amount;
    private String currency;

    // Card payments: render this token inside the Paymob iframe
    private String paymentKeyToken;

    // Tabby / Tamara: redirect the user to this URL
    private String redirectUrl;

    // Card payments only: Paymob iframe ID used to embed the payment form
    private String iframeId;

    public UUID getTransactionId() { return transactionId; }
    public void setTransactionId(UUID transactionId) { this.transactionId = transactionId; }

    public PaymentMethodType getMethodType() { return methodType; }
    public void setMethodType(PaymentMethodType methodType) { this.methodType = methodType; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getPaymentKeyToken() { return paymentKeyToken; }
    public void setPaymentKeyToken(String paymentKeyToken) { this.paymentKeyToken = paymentKeyToken; }

    public String getRedirectUrl() { return redirectUrl; }
    public void setRedirectUrl(String redirectUrl) { this.redirectUrl = redirectUrl; }

    public String getIframeId() { return iframeId; }
    public void setIframeId(String iframeId) { this.iframeId = iframeId; }
}
