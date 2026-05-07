package com.buyology.ecommerce.membership.service;

import com.buyology.ecommerce.common.config.PlatformConfigService;
import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.currency.service.CurrencyExchangeService;
import com.buyology.ecommerce.membership.domain.B2bCountry;
import com.buyology.ecommerce.membership.domain.B2bMembership;
import com.buyology.ecommerce.membership.domain.CreditUsage;
import com.buyology.ecommerce.membership.domain.Wallet;
import com.buyology.ecommerce.membership.repository.B2bCountryRepository;
import com.buyology.ecommerce.membership.repository.B2bMembershipRepository;
import com.buyology.ecommerce.membership.repository.CreditUsageRepository;
import com.buyology.ecommerce.membership.repository.WalletRepository;
import com.buyology.ecommerce.order.domain.Order;
import com.buyology.ecommerce.order.domain.enums.OrderStatus;
import com.buyology.ecommerce.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class B2bCreditOrderService {

    private static final Logger log = LoggerFactory.getLogger(B2bCreditOrderService.class);
    private static final String BASE_CURRENCY = "AED";

    private final OrderRepository orderRepository;
    private final B2bMembershipRepository membershipRepository;
    private final B2bCountryRepository countryRepository;
    private final WalletRepository walletRepository;
    private final WalletService walletService;
    private final CreditUsageRepository creditUsageRepository;
    private final CurrencyExchangeService currencyExchangeService;
    private final PlatformConfigService platformConfigService;

    public B2bCreditOrderService(
            OrderRepository orderRepository,
            B2bMembershipRepository membershipRepository,
            B2bCountryRepository countryRepository,
            WalletRepository walletRepository,
            WalletService walletService,
            CreditUsageRepository creditUsageRepository,
            CurrencyExchangeService currencyExchangeService,
            PlatformConfigService platformConfigService) {
        this.orderRepository = orderRepository;
        this.membershipRepository = membershipRepository;
        this.countryRepository = countryRepository;
        this.walletRepository = walletRepository;
        this.walletService = walletService;
        this.creditUsageRepository = creditUsageRepository;
        this.currencyExchangeService = currencyExchangeService;
        this.platformConfigService = platformConfigService;
    }

    /**
     * Pay (fully or partially) for an order using B2B credit.
     *
     * @param requestedAmount amount of credit to apply (in wallet currency).
     *                        If null, applies the lesser of the order total and the wallet balance.
     */
    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> payOrderWithCredit(
            UUID userId, UUID orderId, BigDecimal requestedAmount) {

        Order order = orderRepository.findByIdAndUserId(orderId, userId).orElse(null);
        if (order == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Order not found");
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return ApiResponse.failure(HttpStatus.CONFLICT,
                    "Order is not awaiting payment (status: " + order.getStatus() + ")");
        }
        if (order.getCreditApplied() != null && order.getCreditApplied().compareTo(BigDecimal.ZERO) > 0) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "Credit has already been applied to this order");
        }

        B2bMembership membership = membershipRepository.findByUserId(userId).orElse(null);
        if (membership == null) {
            return ApiResponse.failure(HttpStatus.FORBIDDEN, "B2B membership required to pay with credit");
        }
        if (membership.getStatus() != B2bMembership.MembershipStatus.ACTIVE) {
            return ApiResponse.failure(HttpStatus.FORBIDDEN,
                    "Membership is not active (status: " + membership.getStatus() + ")");
        }

        Wallet wallet = walletRepository.findByUserId(userId).orElse(null);
        if (wallet == null) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "Wallet not found for member");
        }
        if (wallet.getBalance() == null || wallet.getBalance().compareTo(BigDecimal.ZERO) <= 0) {
            return ApiResponse.failure(HttpStatus.PAYMENT_REQUIRED, "No credit available");
        }

        // Order total expressed in the wallet's currency
        BigDecimal orderTotalInWalletCcy = wallet.getCurrency().equalsIgnoreCase(order.getCurrency())
                ? order.getTotalAmount()
                : currencyExchangeService
                        .convert(order.getTotalAmount(), order.getCurrency(), wallet.getCurrency())
                        .setScale(2, RoundingMode.HALF_UP);

        // Min-order check — per-country threshold first, AED equivalent fallback
        ResponseEntity<ApiResponse<Map<String, Object>>> rejected =
                checkMinOrder(wallet, order, orderTotalInWalletCcy);
        if (rejected != null) return rejected;

        // Resolve how much credit to apply:
        //  - requested = null  → apply min(orderTotal, balance)  (full)
        //  - requested > 0     → apply min(requested, orderTotal, balance)
        BigDecimal creditToApply;
        if (requestedAmount == null || requestedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            creditToApply = orderTotalInWalletCcy.min(wallet.getBalance());
        } else {
            creditToApply = requestedAmount
                    .min(orderTotalInWalletCcy)
                    .min(wallet.getBalance());
        }
        creditToApply = creditToApply.setScale(2, RoundingMode.HALF_UP);

        if (creditToApply.compareTo(BigDecimal.ZERO) <= 0) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Credit amount must be greater than zero");
        }

        // Deduct + ledger entry
        walletService.deductCredit(userId, creditToApply,
                "B2B credit used for order " + orderId, orderId.toString());

        // Record on the order so checkout / order detail can show creditApplied
        order.setCreditApplied(creditToApply);
        order.setCreditCurrency(wallet.getCurrency());

        // Create CreditUsage row (drives payback)
        int paybackDays = platformConfigService.getPaybackDays();
        CreditUsage usage = new CreditUsage();
        usage.setUserId(userId);
        usage.setMembershipId(membership.getId());
        usage.setOrderId(orderId);
        usage.setAmount(creditToApply);
        usage.setCurrency(wallet.getCurrency());
        usage.setStatus(CreditUsage.Status.OUTSTANDING);
        Instant now = Instant.now();
        usage.setUsedAt(now);
        usage.setDueAt(now.plus(paybackDays, ChronoUnit.DAYS));
        usage = creditUsageRepository.save(usage);

        // Did credit cover the full order total?
        boolean fullySettled = creditToApply.compareTo(orderTotalInWalletCcy) >= 0;
        if (fullySettled) {
            order.setStatus(OrderStatus.PAID);
            order.setPaidAt(now);
        }
        // else: order stays PENDING_PAYMENT; checkout will charge the remainder via Paymob.
        orderRepository.save(order);

        log.info("[B2B-CREDIT] Order {} {} with {} {} (usage {}); due in {} days",
                orderId,
                fullySettled ? "fully settled" : "partially credited",
                creditToApply, wallet.getCurrency(), usage.getId(), paybackDays);

        BigDecimal remainingInWalletCcy = orderTotalInWalletCcy.subtract(creditToApply)
                .setScale(2, RoundingMode.HALF_UP);

        Map<String, Object> body = new HashMap<>();
        body.put("orderId", orderId);
        body.put("creditUsageId", usage.getId());
        body.put("creditApplied", creditToApply);
        body.put("creditCurrency", wallet.getCurrency());
        body.put("remainingAmount", remainingInWalletCcy);
        body.put("orderStatus", order.getStatus().name());
        body.put("dueAt", usage.getDueAt());
        body.put("fullySettled", fullySettled);
        return ApiResponse.success(body,
                fullySettled
                        ? "Order paid in full using B2B credit"
                        : "Credit applied; complete the remaining balance via your normal checkout");
    }

    /**
     * Returns null if the order qualifies for credit, or a 400 ApiResponse if not.
     * Per-country threshold (B2bCountry.minOrderAmount in wallet currency) is preferred;
     * falls back to {@code WalletService.B2B_MIN_ORDER_AED} via live FX.
     */
    private ResponseEntity<ApiResponse<Map<String, Object>>> checkMinOrder(
            Wallet wallet, Order order, BigDecimal orderTotalInWalletCcy) {

        if (wallet.getCountryCode() != null) {
            B2bCountry country = countryRepository.findByCountryCode(wallet.getCountryCode()).orElse(null);
            if (country != null && country.getMinOrderAmount() != null) {
                if (orderTotalInWalletCcy.compareTo(country.getMinOrderAmount()) < 0) {
                    return ApiResponse.failure(HttpStatus.BAD_REQUEST,
                            "B2B credit requires a minimum order of "
                                    + country.getMinOrderAmount().setScale(2, RoundingMode.HALF_UP)
                                    + " " + country.getCurrencyCode()
                                    + " (this order: "
                                    + orderTotalInWalletCcy.setScale(2, RoundingMode.HALF_UP)
                                    + " " + wallet.getCurrency() + ")");
                }
                return null; // qualifies via per-country threshold
            }
        }

        // Fallback: AED-equivalent must meet B2B_MIN_ORDER_AED
        BigDecimal totalInAed = BASE_CURRENCY.equalsIgnoreCase(order.getCurrency())
                ? order.getTotalAmount()
                : currencyExchangeService.convert(order.getTotalAmount(), order.getCurrency(), BASE_CURRENCY);
        if (totalInAed.compareTo(WalletService.B2B_MIN_ORDER_AED) < 0) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST,
                    "B2B credit can only be used on orders of AED "
                            + WalletService.B2B_MIN_ORDER_AED.setScale(0, RoundingMode.HALF_UP)
                            + " or more (this order: AED "
                            + totalInAed.setScale(2, RoundingMode.HALF_UP) + ")");
        }
        return null;
    }
}
