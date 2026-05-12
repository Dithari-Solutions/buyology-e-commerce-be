package com.buyology.ecommerce.payout.dto;

import com.buyology.ecommerce.payout.domain.PayoutRequest;
import com.buyology.ecommerce.payout.enums.PayoutRequestStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PayoutRequestResponse(
        UUID id,
        UUID supplierId,
        BigDecimal amountAed,
        String accountSnapshotJson,
        PayoutRequestStatus status,
        String adminNote,
        UUID paidByAdminId,
        Instant paidAt,
        Instant createdAt,
        List<UUID> orderItemIds
) {
    public static PayoutRequestResponse from(PayoutRequest r, List<UUID> orderItemIds) {
        return new PayoutRequestResponse(
                r.getId(),
                r.getSupplierId(),
                r.getAmountAed(),
                r.getAccountSnapshotJson(),
                r.getStatus(),
                r.getAdminNote(),
                r.getPaidByAdminId(),
                r.getPaidAt(),
                r.getCreatedAt(),
                orderItemIds
        );
    }
}
