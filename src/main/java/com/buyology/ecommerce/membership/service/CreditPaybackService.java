package com.buyology.ecommerce.membership.service;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.currency.service.CurrencyExchangeService;
import com.buyology.ecommerce.membership.domain.CreditUsage;
import com.buyology.ecommerce.membership.repository.CreditUsageRepository;
import com.buyology.ecommerce.payment.config.PaymobProperties;
import com.buyology.ecommerce.payment.domain.PaymentProvider;
import com.buyology.ecommerce.payment.domain.PaymentMethodConfig;
import com.buyology.ecommerce.payment.enums.PaymentMethodType;
import com.buyology.ecommerce.payment.repository.PaymentMethodConfigRepository;
import com.buyology.ecommerce.payment.repository.PaymentProviderRepository;
import com.buyology.ecommerce.payment.service.PaymobClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Drives the Paymob payback for an outstanding {@link CreditUsage}.
 * The flow:
 *   1. Member calls {@link #initiatePayback}; we create a Paymob Intention with
 *      {@code merchant_order_id = "CRED-<usageId>"} and return the clientSecret.
 *   2. Member redirects to Paymob; on success Paymob hits the existing webhook.
 *   3. {@link com.buyology.ecommerce.payment.service.PaymentService#handleWebhook}
 *      detects the {@code CRED-} prefix and calls {@link #markPaid}, which
 *      re-credits the wallet and closes the usage.
 */
@Service
public class CreditPaybackService {

    private static final Logger log = LoggerFactory.getLogger(CreditPaybackService.class);
    public static final String MERCHANT_ID_PREFIX = "CRED-";
    private static final String PAYMOB_CURRENCY = "AED";

    private final CreditUsageRepository usageRepository;
    private final WalletService walletService;
    private final CurrencyExchangeService currencyExchangeService;
    private final PaymentProviderRepository providerRepository;
    private final PaymentMethodConfigRepository methodConfigRepository;
    private final PaymobClient paymobClient;
    private final ObjectMapper objectMapper;
    private final PaymobProperties paymobProperties;

    public CreditPaybackService(
            CreditUsageRepository usageRepository,
            WalletService walletService,
            CurrencyExchangeService currencyExchangeService,
            PaymentProviderRepository providerRepository,
            PaymentMethodConfigRepository methodConfigRepository,
            PaymobClient paymobClient,
            ObjectMapper objectMapper,
            PaymobProperties paymobProperties) {
        this.usageRepository = usageRepository;
        this.walletService = walletService;
        this.currencyExchangeService = currencyExchangeService;
        this.providerRepository = providerRepository;
        this.methodConfigRepository = methodConfigRepository;
        this.paymobClient = paymobClient;
        this.objectMapper = objectMapper;
        this.paymobProperties = paymobProperties;
    }

    @Transactional
    public ResponseEntity<ApiResponse<Map<String, Object>>> initiatePayback(
            UUID userId, UUID usageId, BigDecimal requestedAmount, String customerEmail) {

        CreditUsage usage = usageRepository.findById(usageId).orElse(null);
        if (usage == null || !usage.getUserId().equals(userId)) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Credit usage not found");
        }
        if (usage.getStatus() == CreditUsage.Status.PAID) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "Already paid");
        }

        BigDecimal outstanding = usage.getAmount()
                .subtract(usage.getPaidAmount() != null ? usage.getPaidAmount() : BigDecimal.ZERO);
        BigDecimal amountToPay = (requestedAmount != null && requestedAmount.compareTo(BigDecimal.ZERO) > 0
                && requestedAmount.compareTo(outstanding) <= 0)
                ? requestedAmount.setScale(2, RoundingMode.HALF_UP)
                : outstanding.setScale(2, RoundingMode.HALF_UP);

        if (amountToPay.compareTo(BigDecimal.ZERO) <= 0) {
            return ApiResponse.failure(HttpStatus.CONFLICT, "Nothing to pay");
        }

        // Convert to Paymob settlement currency (AED)
        BigDecimal amountInAed = PAYMOB_CURRENCY.equalsIgnoreCase(usage.getCurrency())
                ? amountToPay
                : currencyExchangeService.convert(amountToPay, usage.getCurrency(), PAYMOB_CURRENCY)
                        .setScale(2, RoundingMode.HALF_UP);

        long amountCents = amountInAed.multiply(BigDecimal.valueOf(100)).longValue();

        PaymentProvider provider = providerRepository.findFirstByIsActiveTrue()
                .orElseThrow(() -> new IllegalStateException("No active payment provider"));
        PaymentMethodConfig config = methodConfigRepository
                .findByProviderAndMethodTypeAndIsActiveTrue(provider, PaymentMethodType.CARD)
                .orElseThrow(() -> new IllegalStateException(
                        "No active CARD config for credit payback"));

        String merchantOrderId = MERCHANT_ID_PREFIX + usage.getId();

        ObjectNode billingData = objectMapper.createObjectNode();
        billingData.put("first_name", "B2B");
        billingData.put("last_name", "Member");
        billingData.put("email", customerEmail != null ? customerEmail : "NA");
        billingData.put("phone_number", "NA");
        billingData.put("country", "AE");

        ObjectNode customer = objectMapper.createObjectNode();
        customer.put("email", customerEmail != null ? customerEmail : "NA");
        customer.put("first_name", "B2B");
        customer.put("last_name", "Member");

        ArrayNode items = objectMapper.createArrayNode();
        ObjectNode item = objectMapper.createObjectNode();
        item.put("name", "B2B credit payback (usage " + usage.getId() + ")");
        item.put("amount", amountCents);
        item.put("description", "Repayment of used B2B credit");
        item.put("quantity", 1);
        items.add(item);

        PaymobClient.IntentionResult intention = paymobClient.createIntention(
                provider.getSecretKey(), provider.getBaseUrl(),
                amountCents, PAYMOB_CURRENCY,
                Integer.parseInt(config.getIntegrationId()),
                merchantOrderId,
                billingData, customer, items,
                provider.getNotificationUrl(),
                paymobProperties.getRedirectionUrl());

        usage.setPaymobIntentionId(intention.intentionId());
        usageRepository.save(usage);

        String checkoutUrl = provider.getBaseUrl()
                + "/unifiedcheckout/?publicKey=" + provider.getPublicKey()
                + "&clientSecret=" + intention.clientSecret();

        Map<String, Object> body = new HashMap<>();
        body.put("usageId", usage.getId());
        body.put("amount", amountInAed);
        body.put("currency", PAYMOB_CURRENCY);
        body.put("intentionId", intention.intentionId());
        body.put("clientSecret", intention.clientSecret());
        body.put("checkoutUrl", checkoutUrl);
        return ApiResponse.success(body, "Payback initiated");
    }

    /**
     * Called from PaymentService.handleWebhook when {@code merchant_order_id} starts with
     * {@link #MERCHANT_ID_PREFIX}. Re-credits the wallet, bumps paidAmount, and closes the
     * usage when paid in full.
     */
    @Transactional
    public void markPaid(String merchantOrderId, BigDecimal paidAmountAed) {
        if (merchantOrderId == null || !merchantOrderId.startsWith(MERCHANT_ID_PREFIX)) return;
        UUID usageId;
        try {
            usageId = UUID.fromString(merchantOrderId.substring(MERCHANT_ID_PREFIX.length()));
        } catch (IllegalArgumentException e) {
            log.warn("[PAYBACK] Bad usage id in merchantOrderId {}", merchantOrderId);
            return;
        }
        CreditUsage usage = usageRepository.findById(usageId).orElse(null);
        if (usage == null) {
            log.warn("[PAYBACK] No CreditUsage found for id {}", usageId);
            return;
        }
        if (usage.getStatus() == CreditUsage.Status.PAID) {
            log.info("[PAYBACK] Usage {} already paid — ignoring webhook", usageId);
            return;
        }

        // paidAmountAed is in AED; convert back to wallet currency for the ledger
        BigDecimal paidInWalletCcy = PAYMOB_CURRENCY.equalsIgnoreCase(usage.getCurrency())
                ? paidAmountAed
                : currencyExchangeService.convert(paidAmountAed, PAYMOB_CURRENCY, usage.getCurrency())
                        .setScale(2, RoundingMode.HALF_UP);

        BigDecimal newPaid = (usage.getPaidAmount() != null ? usage.getPaidAmount() : BigDecimal.ZERO)
                .add(paidInWalletCcy);
        usage.setPaidAmount(newPaid);

        if (newPaid.compareTo(usage.getAmount()) >= 0) {
            usage.setStatus(CreditUsage.Status.PAID);
            usage.setPaidAt(Instant.now());
        } else {
            usage.setStatus(CreditUsage.Status.PARTIAL);
        }
        usageRepository.save(usage);

        // Re-credit the wallet so the member can spend the credit again
        walletService.addCredit(
                usage.getUserId(),
                paidInWalletCcy,
                "Credit repayment for usage " + usage.getId(),
                "PAYMOB:" + merchantOrderId);

        log.info("[PAYBACK] Usage {} marked {} (paid {} {})",
                usage.getId(), usage.getStatus(), paidInWalletCcy, usage.getCurrency());
    }
}
