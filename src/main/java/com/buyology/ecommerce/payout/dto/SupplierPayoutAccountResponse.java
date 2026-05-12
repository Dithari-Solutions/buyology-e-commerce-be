package com.buyology.ecommerce.payout.dto;

import com.buyology.ecommerce.payout.domain.SupplierPayoutAccount;

import java.time.Instant;
import java.util.UUID;

public record SupplierPayoutAccountResponse(
        UUID id,
        UUID supplierId,
        String legalName,
        String bankName,
        String accountHolderName,
        String iban,
        String swiftCode,
        String walletProvider,
        String walletNumber,
        Instant submittedAt,
        Instant updatedAt
) {
    public static SupplierPayoutAccountResponse from(SupplierPayoutAccount a) {
        return new SupplierPayoutAccountResponse(
                a.getId(),
                a.getSupplierId(),
                a.getLegalName(),
                a.getBankName(),
                a.getAccountHolderName(),
                a.getIban(),
                a.getSwiftCode(),
                a.getWalletProvider(),
                a.getWalletNumber(),
                a.getSubmittedAt(),
                a.getUpdatedAt()
        );
    }
}
