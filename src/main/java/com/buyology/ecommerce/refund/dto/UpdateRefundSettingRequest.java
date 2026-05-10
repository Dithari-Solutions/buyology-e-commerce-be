package com.buyology.ecommerce.refund.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateRefundSettingRequest(
        @NotNull @Min(1) Integer refundWindowDays,
        @NotNull @DecimalMin("0.0") BigDecimal courierFeeAed,
        Boolean enabled
) {}
