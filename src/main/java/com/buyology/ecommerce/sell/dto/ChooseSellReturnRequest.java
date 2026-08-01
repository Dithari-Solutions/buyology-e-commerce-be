package com.buyology.ecommerce.sell.dto;

import com.buyology.ecommerce.sell.domain.SellDeliveryMethod;
import jakarta.validation.constraints.NotNull;

/**
 * Customer's choice for how a device is returned after they decline the offer. {@code method} must
 * be COURIER_RETURN (20 AED) or STORE_PICKUP (free). {@code currency} is the customer's preferred
 * currency the 20 AED courier fee should be converted into (optional; defaults to AED).
 */
public class ChooseSellReturnRequest {

    @NotNull
    private SellDeliveryMethod method;

    private String currency;

    /** Where Paymob returns the browser after the courier-fee checkout (courier return only). */
    private String redirectionUrl;

    public SellDeliveryMethod getMethod() { return method; }
    public void setMethod(SellDeliveryMethod method) { this.method = method; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getRedirectionUrl() { return redirectionUrl; }
    public void setRedirectionUrl(String redirectionUrl) { this.redirectionUrl = redirectionUrl; }
}
