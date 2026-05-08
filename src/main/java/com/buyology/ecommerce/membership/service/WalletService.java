package com.buyology.ecommerce.membership.service;

import com.buyology.ecommerce.currency.service.CurrencyExchangeService;
import com.buyology.ecommerce.membership.domain.B2bCountry;
import com.buyology.ecommerce.membership.domain.Wallet;
import com.buyology.ecommerce.membership.domain.WalletTransaction;
import com.buyology.ecommerce.membership.dto.WalletResponse;
import com.buyology.ecommerce.membership.dto.WalletTransactionResponse;
import com.buyology.ecommerce.membership.repository.B2bCountryRepository;
import com.buyology.ecommerce.membership.repository.WalletRepository;
import com.buyology.ecommerce.membership.repository.WalletTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class WalletService {

    public static final BigDecimal INITIAL_CREDIT_AED = new BigDecimal("5000.00");
    public static final BigDecimal B2B_MIN_ORDER_AED = new BigDecimal("20000.00");
    private static final String BASE_CURRENCY = "AED";

    private final WalletRepository walletRepo;
    private final WalletTransactionRepository txRepo;
    private final CurrencyExchangeService currencyExchangeService;
    private final B2bCountryRepository countryRepo;

    public WalletService(WalletRepository walletRepo,
                         WalletTransactionRepository txRepo,
                         CurrencyExchangeService currencyExchangeService,
                         B2bCountryRepository countryRepo) {
        this.walletRepo = walletRepo;
        this.txRepo = txRepo;
        this.currencyExchangeService = currencyExchangeService;
        this.countryRepo = countryRepo;
    }

    @Transactional
    public Wallet createWallet(UUID userId) {
        return createWallet(userId, BASE_CURRENCY, null);
    }

    @Transactional
    public Wallet createWallet(UUID userId, String currency, String countryCode) {
        if (walletRepo.existsByUserId(userId)) {
            return walletRepo.findByUserId(userId).orElseThrow();
        }
        Wallet wallet = new Wallet();
        wallet.setUserId(userId);
        wallet.setBalance(BigDecimal.ZERO);
        wallet.setCurrency(currency != null ? currency : BASE_CURRENCY);
        wallet.setCountryCode(countryCode);
        return walletRepo.save(wallet);
    }

    @Transactional
    public WalletResponse addInitialCredit(UUID userId, String performedBy) {
        return addInitialCredit(userId, performedBy, BASE_CURRENCY, null);
    }

    @Transactional
    public WalletResponse addInitialCredit(UUID userId, String performedBy,
                                           String currency, String countryCode) {
        Wallet wallet = walletRepo.findByUserId(userId)
                .orElseGet(() -> createWallet(userId, currency, countryCode));

        // Lock wallet currency to membership's currency at activation time
        if (currency != null && !currency.equals(wallet.getCurrency())) {
            wallet.setCurrency(currency);
            wallet.setCountryCode(countryCode);
            walletRepo.save(wallet);
        }

        BigDecimal creditInWalletCurrency = BASE_CURRENCY.equals(wallet.getCurrency())
                ? INITIAL_CREDIT_AED
                : currencyExchangeService.convert(INITIAL_CREDIT_AED, BASE_CURRENCY, wallet.getCurrency())
                    .setScale(2, RoundingMode.HALF_UP);

        // Persist credit limit so it doesn't drift with later FX moves
        wallet.setCreditLimit(creditInWalletCurrency);
        walletRepo.save(wallet);

        return addCredit(userId, creditInWalletCurrency,
                "Welcome credit for B2B Premium membership", performedBy);
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
        r.setCountryCode(w.getCountryCode());
        r.setCreditLimit(w.getCreditLimit());
        r.setMinOrderAmount(resolveMinOrderAmount(w));
        r.setCreatedAt(w.getCreatedAt());
        r.setUpdatedAt(w.getUpdatedAt());
        return r;
    }

    /**
     * Minimum order amount for this wallet, expressed in the wallet's currency.
     * Mirrors B2bCreditOrderService#checkMinOrder: per-country threshold first,
     * else fall back to the AED-equivalent of {@link #B2B_MIN_ORDER_AED}.
     */
    private BigDecimal resolveMinOrderAmount(Wallet w) {
        if (w.getCountryCode() != null) {
            B2bCountry country = countryRepo.findByCountryCode(w.getCountryCode()).orElse(null);
            if (country != null && country.getMinOrderAmount() != null) {
                return country.getMinOrderAmount().setScale(2, RoundingMode.HALF_UP);
            }
        }
        String currency = w.getCurrency() != null ? w.getCurrency() : BASE_CURRENCY;
        if (BASE_CURRENCY.equalsIgnoreCase(currency)) {
            return B2B_MIN_ORDER_AED.setScale(2, RoundingMode.HALF_UP);
        }
        try {
            return currencyExchangeService.convert(B2B_MIN_ORDER_AED, BASE_CURRENCY, currency)
                    .setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return null;
        }
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
