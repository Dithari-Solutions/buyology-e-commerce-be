package com.buyology.ecommerce.payout.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PayoutEligibilityResponse(
        boolean eligible,
        BigDecimal owedAmountAed,
        LocalDate nextEligibleDate,
        String reason
) {}
