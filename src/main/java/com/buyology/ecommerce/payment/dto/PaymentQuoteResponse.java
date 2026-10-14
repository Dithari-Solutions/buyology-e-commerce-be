package com.buyology.ecommerce.payment.dto;

import java.math.BigDecimal;

public record PaymentQuoteResponse(BigDecimal amount, String currency) {}
