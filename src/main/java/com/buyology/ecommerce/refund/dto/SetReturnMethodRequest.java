package com.buyology.ecommerce.refund.dto;

import com.buyology.ecommerce.refund.enums.RefundReturnMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record SetReturnMethodRequest(
        @NotNull RefundReturnMethod method,
        @Pattern(regexp = "^[A-Za-z]{3}$", message = "currency must be a 3-letter ISO code") String currency,
        // Where Paymob returns the browser after the courier-fee checkout (COURIER_PICKUP only).
        String redirectionUrl
) {}
