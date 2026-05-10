package com.buyology.ecommerce.refund.dto;

import com.buyology.ecommerce.refund.enums.RefundRequestStatus;
import com.buyology.ecommerce.refund.enums.RefundReturnMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RefundRequestResponse(
        UUID id,
        UUID orderId,
        UUID userId,
        String description,
        List<String> imageUrls,
        RefundRequestStatus status,
        RefundReturnMethod returnMethod,
        BigDecimal courierFeeAmount,
        String courierFeeCurrency,
        BigDecimal refundAmount,
        String refundCurrency,
        String adminNote,
        String rejectionReason,
        Instant createdAt,
        Instant approvedAt,
        Instant receivedAt,
        Instant paidAt
) {}
