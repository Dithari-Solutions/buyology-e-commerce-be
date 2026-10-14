package com.buyology.ecommerce.payment.dto;

import com.buyology.ecommerce.payment.enums.PaymentMethodType;
import java.util.List;

/** Checkout capabilities only; no provider credentials or integration configuration. */
public record AvailablePaymentMethodsResponse(List<PaymentMethodType> methods, String currency) {}
