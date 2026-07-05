package com.buyology.ecommerce.b2b.quote.dto;

import com.buyology.ecommerce.payment.enums.PaymentMethodType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Checkout an ACCEPTED quote. Mirrors the consumer order-first flow: the caller supplies
 * a delivery address (or a pickup store) plus the payment method, and the service creates
 * the order from the quoted lines and initiates payment on it.
 */
public class CheckoutQuoteRequest {

    /** Delivery method: DELIVERY (default) requires addressId; PICKUP requires pickupStoreId. */
    private String deliveryMethod;

    /** Required for delivery. */
    private UUID addressId;

    /** Required for pickup. */
    private UUID pickupStoreId;

    @NotNull
    private PaymentMethodType methodType;

    /** Optional — billing email passed to the payment gateway. */
    private String customerEmail;

    /** Optional — where Paymob redirects after checkout (web vs mobile). */
    private String redirectionUrl;

    public String getDeliveryMethod() { return deliveryMethod; }
    public void setDeliveryMethod(String deliveryMethod) { this.deliveryMethod = deliveryMethod; }

    public UUID getAddressId() { return addressId; }
    public void setAddressId(UUID addressId) { this.addressId = addressId; }

    public UUID getPickupStoreId() { return pickupStoreId; }
    public void setPickupStoreId(UUID pickupStoreId) { this.pickupStoreId = pickupStoreId; }

    public PaymentMethodType getMethodType() { return methodType; }
    public void setMethodType(PaymentMethodType methodType) { this.methodType = methodType; }

    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }

    public String getRedirectionUrl() { return redirectionUrl; }
    public void setRedirectionUrl(String redirectionUrl) { this.redirectionUrl = redirectionUrl; }
}
