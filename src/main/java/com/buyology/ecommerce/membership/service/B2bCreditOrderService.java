package com.buyology.ecommerce.membership.service;

import com.buyology.ecommerce.common.config.PlatformConfigService;
import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.currency.service.CurrencyExchangeService;
import com.buyology.ecommerce.membership.domain.B2bMembership;
import com.buyology.ecommerce.membership.domain.CreditUsage;
import com.buyology.ecommerce.membership.domain.Wallet;
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
    private final WalletRepository walletRepository;
    private final WalletService walletService;
    private final CreditUsageRepository creditUsageRepository;
    private final CurrencyExchangeService currencyExchangeService;
    private final PlatformConfigService platformConfigService;

    public B2bCreditOrderService(
            OrderRepository orderRepository,
            B2bMembershipRepository membershipRepository,
            WalletRepository walletRepository,
            WalletService walletService,
            CreditUsageRepository creditUsageRepository,
            CurrencyExchangeService currencyExchangeService,
            PlatformConfigService platformConfigService) {
        this.orderRepository = orderRepository;
        this.membershipRepository = membershipRepository;
        this.walletRepository = walletRepository;
        this.walletService = walletService;
        this.creditUsageRepository = creditUsageRepository;
        this.currencyExchangeService = currencyExchangeService;
        this.platformConfigService = platformConfigService;
    }

    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> payOrderWithCredit(UUID userId, UUID orderId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId).orElse(null);
        if (order == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Order not found");
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return ApiResponse.failure(HttpStatus.CONFLICT,
                    "Order is not awaiting payment (status: " + order.getStatus() + ")");
        }

        B2bMembership membership = membershipRepository.findByUserId(userId).orElse(null);
        if (membership == null) {
            return ApiResponse.failure(HttpStatus.FORBIDDEN, "B2B membership required to pay with credit");
        }
        if (membership.getStatus() != B2bMembership.MembershipStatus.ACTIVE) {
            return ApiResponse.failure(HttpStatus.FORBIDDEN,
                    "Membership is not active (status: " + membership.getStatus() + ")");
        }

        // Min-order check: convert order total to AED, must be >= 20K
        BigDecimal totalInAed = BASE_CURRENCY.equalsIgnoreCase(order.getCurrency())
                ? order.getTotalAmount()
                : currencyExchangeService.convert(order.getTotalAmount(), order.getCurrency(), BASE_CURRENCY);
        if (totalInAed.compareTo(WalletService.B2B_MIN_ORDER_AED) < 0) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST,
                    "B2B credit can only be used on orders of AED 20,000 or more (this order: AED "
                            + totalInAed.setScale(2, RoundingMode.HALF_UP) + ")");
        }

        // Convert order total to wallet currency for deduction + ledger
        Wallet wallet = walletRepository.findByUserId(userId).orElse(null);
        if (wallet == null) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "Wallet not found for member");
        }
        BigDecimal amountInWalletCurrency = wallet.getCurrency().equalsIgnoreCase(order.getCurrency())
                ? order.getTotalAmount()
                : currencyExchangeService.convert(order.getTotalAmount(), order.getCurrency(), wallet.getCurrency())
                    .setScale(2, RoundingMode.HALF_UP);

        if (wallet.getBalance().compareTo(amountInWalletCurrency) < 0) {
            return ApiResponse.failure(HttpStatus.PAYMENT_REQUIRED,
                    "Insufficient credit. Available: " + wallet.getBalance() + " " + wallet.getCurrency());
        }

        // Deduct + ledger entry
        walletService.deductCredit(userId, amountInWalletCurrency,
                "B2B credit used for order " + orderId, orderId.toString());

        // Create the CreditUsage row that drives the payback flow
        int paybackDays = platformConfigService.getPaybackDays();
        CreditUsage usage = new CreditUsage();
        usage.setUserId(userId);
        usage.setMembershipId(membership.getId());
        usage.setOrderId(orderId);
        usage.setAmount(amountInWalletCurrency);
        usage.setCurrency(wallet.getCurrency());
        usage.setStatus(CreditUsage.Status.OUTSTANDING);
        Instant now = Instant.now();
        usage.setUsedAt(now);
        usage.setDueAt(now.plus(paybackDays, ChronoUnit.DAYS));
        usage = creditUsageRepository.save(usage);

        // Transition order to PAID (no PaymentTransaction — credit settles instantly)
        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(Instant.now());
        orderRepository.save(order);

        log.info("[B2B-CREDIT] Order {} settled with credit (usage {}); due in {} days",
                orderId, usage.getId(), paybackDays);

        Map<String, Object> body = new HashMap<>();
        body.put("orderId", orderId);
        body.put("creditUsageId", usage.getId());
        body.put("amount", amountInWalletCurrency);
        body.put("currency", wallet.getCurrency());
        body.put("dueAt", usage.getDueAt());
        return ApiResponse.success(body, "Order paid using B2B credit");
    }
}
