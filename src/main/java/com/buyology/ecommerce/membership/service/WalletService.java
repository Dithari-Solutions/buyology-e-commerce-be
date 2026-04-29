package com.buyology.ecommerce.membership.service;

import com.buyology.ecommerce.membership.domain.Wallet;
import com.buyology.ecommerce.membership.domain.WalletTransaction;
import com.buyology.ecommerce.membership.dto.WalletResponse;
import com.buyology.ecommerce.membership.dto.WalletTransactionResponse;
import com.buyology.ecommerce.membership.repository.WalletRepository;
import com.buyology.ecommerce.membership.repository.WalletTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class WalletService {

    private static final BigDecimal INITIAL_CREDIT = new BigDecimal("5000.00");

    private final WalletRepository walletRepo;
    private final WalletTransactionRepository txRepo;

    public WalletService(WalletRepository walletRepo, WalletTransactionRepository txRepo) {
        this.walletRepo = walletRepo;
        this.txRepo = txRepo;
    }

    @Transactional
    public Wallet createWallet(UUID userId) {
        if (walletRepo.existsByUserId(userId)) {
            return walletRepo.findByUserId(userId).orElseThrow();
        }
        Wallet wallet = new Wallet();
        wallet.setUserId(userId);
        wallet.setBalance(BigDecimal.ZERO);
        wallet.setCurrency("AED");
        return walletRepo.save(wallet);
    }

    @Transactional
    public WalletResponse addInitialCredit(UUID userId, String performedBy) {
        walletRepo.findByUserId(userId).orElseGet(() -> createWallet(userId));
        return addCredit(userId, INITIAL_CREDIT, "Welcome credit for B2B Premium membership", performedBy);
    }

    @Transactional
    public WalletResponse addCredit(UUID userId, BigDecimal amount, String description, String performedBy) {
        Wallet wallet = walletRepo.findByUserId(userId)
                .orElseThrow(() -> new NoSuchElementException("Wallet not found for user: " + userId));

        BigDecimal newBalance = wallet.getBalance().add(amount);
        wallet.setBalance(newBalance);
        walletRepo.save(wallet);

        recordTransaction(wallet, WalletTransaction.TransactionType.CREDIT, amount, newBalance, description, performedBy, null);
        return toResponse(wallet);
    }

    @Transactional
    public WalletResponse deductCredit(UUID userId, BigDecimal amount, String description, String referenceId) {
        Wallet wallet = walletRepo.findByUserId(userId)
                .orElseThrow(() -> new NoSuchElementException("Wallet not found for user: " + userId));

        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient wallet balance");
        }

        BigDecimal newBalance = wallet.getBalance().subtract(amount);
        wallet.setBalance(newBalance);
        walletRepo.save(wallet);

        recordTransaction(wallet, WalletTransaction.TransactionType.DEBIT, amount, newBalance, description, null, referenceId);
        return toResponse(wallet);
    }

    @Transactional
    public WalletResponse adjustment(UUID userId, BigDecimal amount, String description, String performedBy) {
        Wallet wallet = walletRepo.findByUserId(userId)
                .orElseThrow(() -> new NoSuchElementException("Wallet not found for user: " + userId));

        BigDecimal newBalance = wallet.getBalance().add(amount);
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException("Adjustment would result in negative balance");
        }
        wallet.setBalance(newBalance);
        walletRepo.save(wallet);

        recordTransaction(wallet, WalletTransaction.TransactionType.ADJUSTMENT, amount, newBalance, description, performedBy, null);
        return toResponse(wallet);
    }

    public WalletResponse getWallet(UUID userId) {
        Wallet wallet = walletRepo.findByUserId(userId)
                .orElseThrow(() -> new NoSuchElementException("Wallet not found for user: " + userId));
        return toResponse(wallet);
    }

    public List<WalletTransactionResponse> getTransactions(UUID userId) {
        return txRepo.findByUserIdOrderByCreatedAtDesc(userId)
                .stream().map(this::toTxResponse).collect(Collectors.toList());
    }

    private void recordTransaction(Wallet wallet, WalletTransaction.TransactionType type,
                                   BigDecimal amount, BigDecimal balanceAfter,
                                   String description, String performedBy, String referenceId) {
        WalletTransaction tx = new WalletTransaction();
        tx.setWalletId(wallet.getId());
        tx.setUserId(wallet.getUserId());
        tx.setType(type);
        tx.setAmount(amount);
        tx.setBalanceAfter(balanceAfter);
        tx.setDescription(description);
        tx.setPerformedBy(performedBy);
        tx.setReferenceId(referenceId);
        txRepo.save(tx);
    }

    public WalletResponse toResponse(Wallet w) {
        WalletResponse r = new WalletResponse();
        r.setId(w.getId());
        r.setUserId(w.getUserId());
        r.setBalance(w.getBalance());
        r.setCurrency(w.getCurrency());
        r.setCreatedAt(w.getCreatedAt());
        r.setUpdatedAt(w.getUpdatedAt());
        return r;
    }

    private WalletTransactionResponse toTxResponse(WalletTransaction t) {
        WalletTransactionResponse r = new WalletTransactionResponse();
        r.setId(t.getId());
        r.setWalletId(t.getWalletId());
        r.setUserId(t.getUserId());
        r.setType(t.getType());
        r.setAmount(t.getAmount());
        r.setBalanceAfter(t.getBalanceAfter());
        r.setDescription(t.getDescription());
        r.setReferenceId(t.getReferenceId());
        r.setPerformedBy(t.getPerformedBy());
        r.setCreatedAt(t.getCreatedAt());
        return r;
    }
}
