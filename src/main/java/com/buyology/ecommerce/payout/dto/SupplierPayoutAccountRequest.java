package com.buyology.ecommerce.payout.dto;

import jakarta.validation.constraints.NotBlank;

public record SupplierPayoutAccountRequest(
        @NotBlank String legalName,
        String bankName,
        String accountHolderName,
        String iban,
        String swiftCode,
        String walletProvider,
        String walletNumber
) {}
