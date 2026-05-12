package com.buyology.ecommerce.payout.service;

import com.buyology.ecommerce.payout.domain.SupplierPayoutAccount;
import com.buyology.ecommerce.payout.dto.SupplierPayoutAccountRequest;
import com.buyology.ecommerce.payout.dto.SupplierPayoutAccountResponse;
import com.buyology.ecommerce.payout.exception.PayoutException;
import com.buyology.ecommerce.payout.repository.SupplierPayoutAccountRepository;
import com.buyology.ecommerce.supplier.domain.Supplier;
import com.buyology.ecommerce.supplier.repository.SupplierRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class SupplierPayoutAccountService {

    private final SupplierPayoutAccountRepository repository;
    private final SupplierRepository supplierRepository;

    public SupplierPayoutAccountService(SupplierPayoutAccountRepository repository,
                                        SupplierRepository supplierRepository) {
        this.repository = repository;
        this.supplierRepository = supplierRepository;
    }

    @Transactional(readOnly = true)
    public Optional<SupplierPayoutAccountResponse> getForCurrentSupplier() {
        Supplier supplier = requireCurrentSupplier();
        return repository.findBySupplierId(supplier.getId()).map(SupplierPayoutAccountResponse::from);
    }

    @Transactional(readOnly = true)
    public Optional<SupplierPayoutAccountResponse> getForSupplier(UUID supplierId) {
        return repository.findBySupplierId(supplierId).map(SupplierPayoutAccountResponse::from);
    }

    @Transactional
    public SupplierPayoutAccountResponse upsertForCurrentSupplier(SupplierPayoutAccountRequest request) {
        Supplier supplier = requireCurrentSupplier();
        if ((isBlank(request.iban()) && isBlank(request.walletNumber()))) {
            throw new PayoutException("Provide either an IBAN/bank details or a wallet number");
        }
        SupplierPayoutAccount account = repository.findBySupplierId(supplier.getId())
                .orElseGet(SupplierPayoutAccount::new);
        account.setSupplierId(supplier.getId());
        account.setLegalName(request.legalName().trim());
        account.setBankName(trimOrNull(request.bankName()));
        account.setAccountHolderName(trimOrNull(request.accountHolderName()));
        account.setIban(trimOrNull(request.iban()));
        account.setSwiftCode(trimOrNull(request.swiftCode()));
        account.setWalletProvider(trimOrNull(request.walletProvider()));
        account.setWalletNumber(trimOrNull(request.walletNumber()));
        return SupplierPayoutAccountResponse.from(repository.save(account));
    }

    public Supplier requireCurrentSupplier() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UUID userId)) {
            throw new PayoutException("Supplier account not found");
        }
        return supplierRepository.findByUserId(userId)
                .orElseThrow(() -> new PayoutException("Supplier account not found"));
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
