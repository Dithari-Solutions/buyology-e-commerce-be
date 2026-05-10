package com.buyology.ecommerce.refund.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RefundSettingResponse(
        UUID id,
        Integer refundWindowDays,
        BigDecimal courierFeeAed,
        String courierFeeCurrency,
        boolean enabled,
        UUID updatedBy,
        Instant updatedAt
) {}
