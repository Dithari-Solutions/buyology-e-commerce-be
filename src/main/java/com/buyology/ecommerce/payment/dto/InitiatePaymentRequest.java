package com.buyology.ecommerce.payment.dto;

import com.buyology.ecommerce.payment.enums.PaymentMethodType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public class InitiatePaymentRequest {

    // Optional — null for the cart-first flow where the order is created after payment success.
    private UUID appOrderId;

    // Cart-first flow: provide cartId + addressId + deliveryMethod instead of appOrderId.
    @NotNull
    private UUID cartId;

    @NotNull
    private UUID addressId;

    @NotNull
    private String deliveryMethod;

    private java.math.BigDecimal shippingFee;

    @NotNull
    private PaymentMethodType methodType;

    @NotNull
    @DecimalMin("0.01")
    private BigDecimal amount;

    @NotNull
    @Size(min = 3, max = 3)
    private String currency;

    @NotNull
    private UUID customerId;

    @NotNull
    private String customerEmail;

    private String customerPhone;

    @NotNull
    private String billingName;

    // Billing address — passed to Paymob but not stored in this service
    private String billingApartment;
    private String billingFloor;
    private String billingStreet;
    private String billingBuilding;
    private String billingCity;
    private String billingCountry;
    private String billingState;
    private String billingPostalCode;

    public UUID getAppOrderId() { return appOrderId; }
    public void setAppOrderId(UUID appOrderId) { this.appOrderId = appOrderId; }

    public UUID getCartId() { return cartId; }
    public void setCartId(UUID cartId) { this.cartId = cartId; }

    public UUID getAddressId() { return addressId; }
    public void setAddressId(UUID addressId) { this.addressId = addressId; }

    public String getDeliveryMethod() { return deliveryMethod; }
    public void setDeliveryMethod(String deliveryMethod) { this.deliveryMethod = deliveryMethod; }

    public java.math.BigDecimal getShippingFee() { return shippingFee; }
    public void setShippingFee(java.math.BigDecimal shippingFee) { this.shippingFee = shippingFee; }

    public PaymentMethodType getMethodType() { return methodType; }
    public void setMethodType(PaymentMethodType methodType) { this.methodType = methodType; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }

    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }

    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }

    public String getBillingName() { return billingName; }
    public void setBillingName(String billingName) { this.billingName = billingName; }

    public String getBillingApartment() { return billingApartment; }
    public void setBillingApartment(String billingApartment) { this.billingApartment = billingApartment; }

    public String getBillingFloor() { return billingFloor; }
    public void setBillingFloor(String billingFloor) { this.billingFloor = billingFloor; }

    public String getBillingStreet() { return billingStreet; }
    public void setBillingStreet(String billingStreet) { this.billingStreet = billingStreet; }

    public String getBillingBuilding() { return billingBuilding; }
    public void setBillingBuilding(String billingBuilding) { this.billingBuilding = billingBuilding; }

    public String getBillingCity() { return billingCity; }
    public void setBillingCity(String billingCity) { this.billingCity = billingCity; }

    public String getBillingCountry() { return billingCountry; }
    public void setBillingCountry(String billingCountry) { this.billingCountry = billingCountry; }

    public String getBillingState() { return billingState; }
    public void setBillingState(String billingState) { this.billingState = billingState; }

    public String getBillingPostalCode() { return billingPostalCode; }
    public void setBillingPostalCode(String billingPostalCode) { this.billingPostalCode = billingPostalCode; }
}
