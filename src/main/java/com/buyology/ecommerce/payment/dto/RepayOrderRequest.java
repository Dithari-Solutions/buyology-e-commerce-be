package com.buyology.ecommerce.payment.dto;

import com.buyology.ecommerce.payment.enums.PaymentMethodType;
import jakarta.validation.constraints.NotNull;

/** Re-initiate payment for an existing PENDING_PAYMENT order (customer "pay again"). */
public class RepayOrderRequest {

    @NotNull
    private PaymentMethodType methodType;

    /** Optional — used for the Paymob customer/billing email; falls back to "NA". */
    private String customerEmail;

    private String redirectionUrl;

    public PaymentMethodType getMethodType() { return methodType; }
    public void setMethodType(PaymentMethodType methodType) { this.methodType = methodType; }

    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }

    public String getRedirectionUrl() { return redirectionUrl; }
    public void setRedirectionUrl(String redirectionUrl) { this.redirectionUrl = redirectionUrl; }
}
