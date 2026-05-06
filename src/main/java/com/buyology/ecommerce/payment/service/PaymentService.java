package com.buyology.ecommerce.payment.service;

import com.buyology.ecommerce.order.event.PaymentSucceededEvent;
import com.buyology.ecommerce.payment.domain.*;
import com.buyology.ecommerce.payment.dto.*;
import com.buyology.ecommerce.payment.enums.PaymentMethodType;
import com.buyology.ecommerce.payment.enums.PaymentStatus;
import com.buyology.ecommerce.payment.enums.RefundStatus;
import com.buyology.ecommerce.payment.repository.*;
import com.buyology.ecommerce.currency.service.CurrencyExchangeService;
import com.buyology.ecommerce.user.domain.UserAddress;
import com.buyology.ecommerce.user.repository.UserAddressRepository;
import com.buyology.ecommerce.user.service.UserProfileService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentProviderRepository providerRepo;
    private final PaymentMethodConfigRepository methodConfigRepo;
    private final PaymentTransactionRepository transactionRepo;
    private final PaymentWebhookEventRepository webhookEventRepo;
    private final PaymentRefundRepository refundRepo;
    private final PaymobClient paymobClient;
    private final ObjectMapper objectMapper;
    private final UserProfileService userProfileService;
    private final UserAddressRepository addressRepo;
    private final ApplicationEventPublisher eventPublisher;
    private final CurrencyExchangeService currencyExchangeService;
    private final com.buyology.ecommerce.payment.config.PaymobProperties paymobProperties;
    private final org.springframework.beans.factory.ObjectProvider<com.buyology.ecommerce.membership.service.CreditPaybackService> creditPaybackProvider;

    public PaymentService(
            PaymentProviderRepository providerRepo,
            PaymentMethodConfigRepository methodConfigRepo,
            PaymentTransactionRepository transactionRepo,
            PaymentWebhookEventRepository webhookEventRepo,
            PaymentRefundRepository refundRepo,
            PaymobClient paymobClient,
            ObjectMapper objectMapper,
            UserProfileService userProfileService,
            UserAddressRepository addressRepo,
            ApplicationEventPublisher eventPublisher,
            CurrencyExchangeService currencyExchangeService,
            com.buyology.ecommerce.payment.config.PaymobProperties paymobProperties,
            org.springframework.beans.factory.ObjectProvider<com.buyology.ecommerce.membership.service.CreditPaybackService> creditPaybackProvider) {
        this.providerRepo = providerRepo;
        this.methodConfigRepo = methodConfigRepo;
        this.transactionRepo = transactionRepo;
        this.webhookEventRepo = webhookEventRepo;
        this.refundRepo = refundRepo;
        this.paymobClient = paymobClient;
        this.objectMapper = objectMapper;
        this.userProfileService = userProfileService;
        this.addressRepo = addressRepo;
        this.eventPublisher = eventPublisher;
        this.currencyExchangeService = currencyExchangeService;
        this.paymobProperties = paymobProperties;
        this.creditPaybackProvider = creditPaybackProvider;
    }

    // =========================================================================
    // Initiate payment — Paymob Intention API (v2, single-call flow)
    // =========================================================================

    @Transactional
    public PaymentInitiatedResponse initiatePayment(InitiatePaymentRequest req) {
        userProfileService.checkPaymentReadiness(req.getCustomerId());

        PaymentProvider provider = providerRepo.findFirstByIsActiveTrue()
                .orElseThrow(() -> new IllegalStateException("No active payment provider configured"));

        PaymentMethodConfig config = methodConfigRepo
                .findByProviderAndMethodTypeAndIsActiveTrue(provider, req.getMethodType())
                .orElseThrow(() -> new IllegalStateException(
                        "No active config for method: " + req.getMethodType()));

        String targetCurrency = "AED";
        BigDecimal convertedAmount = currencyExchangeService.convert(req.getAmount(), req.getCurrency(), targetCurrency);

        long amountCents = convertedAmount
                .multiply(BigDecimal.valueOf(100))
                .longValue();

        UserAddress address = req.getAddressId() != null
                ? addressRepo.findById(req.getAddressId()).orElse(null)
                : null;
        ObjectNode billingData = buildBillingData(req, address);

        ObjectNode customer = objectMapper.createObjectNode();
        customer.put("email", req.getCustomerEmail() != null ? req.getCustomerEmail() : "NA");
        String[] nameParts = req.getBillingName() != null
                ? req.getBillingName().split(" ", 2)
                : new String[]{"NA", "NA"};
        customer.put("first_name", nameParts[0]);
        customer.put("last_name", nameParts.length > 1 ? nameParts[1] : "NA");
        if (req.getCustomerPhone() != null) {
            customer.put("phone_number", req.getCustomerPhone());
        }

        ArrayNode items = objectMapper.createArrayNode();
        ObjectNode item = objectMapper.createObjectNode();
        item.put("name", "Order " + (req.getAppOrderId() != null ? req.getAppOrderId() : "from cart"));
        item.put("amount", amountCents);
        item.put("description", "Payment for order");
        item.put("quantity", 1);
        items.add(item);

        int integrationId = Integer.parseInt(config.getIntegrationId());
        
        // 1. Save pending transaction first to get a UUID for merchant_order_id
        PaymentTransaction tx = savePendingTransaction(req, provider, config, convertedAmount, amountCents, targetCurrency);

        String redirectionUrl = req.getRedirectionUrl() != null && !req.getRedirectionUrl().isBlank()
                ? req.getRedirectionUrl()
                : paymobProperties.getRedirectionUrl();

        // 2. Call Paymob API using tx.id as merchant_order_id
        PaymobClient.IntentionResult intention = paymobClient.createIntention(
                provider.getSecretKey(), provider.getBaseUrl(),
                amountCents, targetCurrency,
                integrationId, tx.getId().toString(),
                billingData, customer, items,
                provider.getNotificationUrl(),
                redirectionUrl);

        log.info("[PAYMENT] Created Paymob Intention: id={}, paymobOrderId={}", intention.intentionId(), intention.paymobOrderId());

        // 3. Update transaction with Paymob IDs
        return finalizeTransactionWithProvider(tx.getId(), intention);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentTransaction savePendingTransaction(InitiatePaymentRequest req, 
                                                     PaymentProvider provider, 
                                                     PaymentMethodConfig config,
                                                     BigDecimal amount,
                                                     long amountCents,
                                                     String currency) {
        PaymentTransaction tx = new PaymentTransaction();
        tx.setAppOrderId(req.getAppOrderId());
        tx.setCartId(req.getCartId());
        tx.setMethodConfig(config);
        tx.setMethodType(req.getMethodType());
        tx.setAmount(amount);
        tx.setAmountCents(amountCents);
        tx.setCurrency(currency);
        tx.setStatus(PaymentStatus.PENDING);
        tx.setCustomerId(req.getCustomerId());
        tx.setCustomerEmail(req.getCustomerEmail());
        tx.setCustomerPhone(req.getCustomerPhone());
        tx.setBillingName(req.getBillingName());
        tx.setMetadata(buildOrderMetadata(req));
        
        tx = transactionRepo.save(tx);
        log.info("[PAYMENT] Committed PENDING transaction: id={}, cartId={}", tx.getId(), tx.getCartId());
        return tx;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentInitiatedResponse finalizeTransactionWithProvider(UUID transactionId, 
                                                                   PaymobClient.IntentionResult intention) {
        PaymentTransaction tx = transactionRepo.findById(transactionId)
                .orElseThrow(() -> new NoSuchElementException("Transaction not found: " + transactionId));
        
        tx.setIntentionId(intention.intentionId());
        tx.setPaymobOrderId(intention.paymobOrderId());
        tx.setPaymentKeyToken(intention.clientSecret());
        transactionRepo.save(tx);

        String checkoutUrl = tx.getMethodConfig().getProvider().getBaseUrl()
                + "/unifiedcheckout/?publicKey=" + tx.getMethodConfig().getProvider().getPublicKey()
                + "&clientSecret=" + intention.clientSecret();

        return buildInitiatedResponse(tx, intention.clientSecret(), checkoutUrl);
    }

    // =========================================================================
    // Webhook handling
    // =========================================================================

    @Transactional
    public void handleWebhook(String rawPayload, String receivedHmac) {
        PaymentProvider provider = providerRepo.findFirstByIsActiveTrue()
                .orElseThrow(() -> new IllegalStateException("No active payment provider"));

        boolean hmacValid = validateHmac(rawPayload, receivedHmac, provider.getHmacSecret());

        JsonNode root;
        try {
            root = objectMapper.readTree(rawPayload);
        } catch (Exception e) {
            log.error("[WEBHOOK] Failed to parse payload JSON", e);
            throw new RuntimeException("Invalid JSON", e);
        }

        JsonNode obj = root.has("obj") ? root.get("obj") : root;
        String providerTxnIdStr = obj.has("id") ? obj.get("id").asText() : null;
        Long providerTxnId = (providerTxnIdStr != null) ? Long.valueOf(providerTxnIdStr) : null;

        // 0. B2B credit payback short-circuit
        // CreditPaybackService creates Paymob intentions with merchant_order_id="CRED-<usageId>"
        // and does NOT create a PaymentTransaction row. Detect that and route to the payback flow.
        String maybeMoid = null;
        if (obj.has("order") && obj.get("order").has("merchant_order_id")) {
            maybeMoid = obj.get("order").get("merchant_order_id").asText();
        }
        if (maybeMoid != null && maybeMoid.startsWith(
                com.buyology.ecommerce.membership.service.CreditPaybackService.MERCHANT_ID_PREFIX)) {
            if (!hmacValid) {
                log.warn("[WEBHOOK][CRED] Invalid HMAC for {}", maybeMoid);
                saveWebhookEvent(provider, null, providerTxnIdStr, hmacValid, rawPayload, "Invalid HMAC (CRED)");
                return;
            }
            boolean success = obj.has("success") && obj.get("success").asBoolean();
            if (!success) {
                log.info("[WEBHOOK][CRED] Non-success webhook for {}, skipping", maybeMoid);
                saveWebhookEvent(provider, null, providerTxnIdStr, hmacValid, rawPayload, "non-success");
                return;
            }
            long amountCents = obj.has("amount_cents") ? obj.get("amount_cents").asLong() : 0L;
            BigDecimal amountAed = BigDecimal.valueOf(amountCents).movePointLeft(2);
            try {
                creditPaybackProvider.getObject().markPaid(maybeMoid, amountAed);
                saveWebhookEvent(provider, null, providerTxnIdStr, hmacValid, rawPayload, null);
            } catch (Exception e) {
                log.error("[WEBHOOK][CRED] markPaid failed for {}", maybeMoid, e);
                saveWebhookEvent(provider, null, providerTxnIdStr, hmacValid, rawPayload, e.getMessage());
            }
            return;
        }

        // 1. Robust Idempotency check using numeric paymob_transaction_id
        if (providerTxnId != null) {
            Optional<PaymentTransaction> existing = transactionRepo.findByPaymobTransactionId(providerTxnId);
            if (existing.isPresent() && isTerminal(existing.get().getStatus())) {
                log.info("[WEBHOOK] Paymob transaction {} already processed. Status={}", providerTxnId, existing.get().getStatus());
                return;
            }
        }

        // 2. Resolve Transaction
        PaymentTransaction transaction = resolveTransactionFromPayload(root);

        if (transaction == null) {
            log.error("[WEBHOOK] Could not resolve transaction from payload. raw={}", rawPayload);
            saveWebhookEvent(provider, null, providerTxnIdStr, hmacValid, rawPayload, "Unresolved transaction");
            return;
        }

        log.info("[WEBHOOK] Matched to Transaction: id={}, currentStatus={}", transaction.getId(), transaction.getStatus());

        if (isTerminal(transaction.getStatus())) {
            log.info("[WEBHOOK] Transaction {} already terminal ({}). Skipping.", transaction.getId(), transaction.getStatus());
            saveWebhookEvent(provider, transaction, providerTxnIdStr, hmacValid, rawPayload, null);
            return;
        }

        if (!hmacValid) {
            log.warn("[WEBHOOK] HMAC invalid for transaction {}", transaction.getId());
            saveWebhookEvent(provider, transaction, providerTxnIdStr, hmacValid, rawPayload, "Invalid HMAC");
            return;
        }

        // 3. Process status update
        try {
            applyWebhookToTransaction(transaction, obj, providerTxnId);
            transactionRepo.saveAndFlush(transaction);

            log.info("[WEBHOOK] Transaction {} updated to {}", transaction.getId(), transaction.getStatus());

            if (transaction.getStatus() == PaymentStatus.SUCCESS) {
                log.info("[WEBHOOK] SUCCESS! Publishing PaymentSucceededEvent.");
                eventPublisher.publishEvent(new PaymentSucceededEvent(transaction.getAppOrderId(), transaction.getId()));
            }

            saveWebhookEvent(provider, transaction, providerTxnIdStr, hmacValid, rawPayload, null);
        } catch (Exception e) {
            log.error("[WEBHOOK] Error processing transaction {}", transaction.getId(), e);
            saveWebhookEvent(provider, transaction, providerTxnIdStr, hmacValid, rawPayload, e.getMessage());
            throw e;
        }
    }

    private void saveWebhookEvent(PaymentProvider provider, PaymentTransaction tx, String txnId, boolean hmac, String payload, String error) {
        PaymentWebhookEvent event = new PaymentWebhookEvent();
        event.setProvider(provider);
        event.setTransaction(tx);
        event.setProviderTxnId(txnId);
        event.setHmacValid(hmac);
        event.setPayload(payload);
        event.setError(error);
        event.setProcessed(error == null);
        event.setProcessedAt(Instant.now());
        webhookEventRepo.save(event);
    }

    private PaymentTransaction resolveTransactionFromPayload(JsonNode root) {
        JsonNode obj = root.has("obj") ? root.get("obj") : root;
        
        // Priority 1: merchant_order_id (Our internal UUID string)
        if (obj.has("order") && obj.get("order").has("merchant_order_id")) {
            String moid = obj.get("order").get("merchant_order_id").asText();
            if (moid != null && !moid.isBlank() && !moid.equals("null")) {
                return transactionRepo.findByMerchantOrderId(moid).orElse(null);
            }
        }

        // Priority 2: paymob_order_id (Numeric)
        if (obj.has("order") && obj.get("order").has("id")) {
            long poid = obj.get("order").get("id").asLong();
            return transactionRepo.findByPaymobOrderId(poid).orElse(null);
        }

        // Priority 3: intention_id (pi_live_*)
        if (root.has("intention_id")) {
            String iid = root.get("intention_id").asText();
            return transactionRepo.findByIntentionId(iid).orElse(null);
        }

        return null;
    }

    private void applyWebhookToTransaction(PaymentTransaction tx, JsonNode obj, Long paymobTxnId) {
        tx.setPaymobTransactionId(paymobTxnId);
        
        boolean success = obj.has("success") && obj.get("success").asBoolean();
        boolean pending = obj.has("pending") && obj.get("pending").asBoolean();

        if (success) {
            tx.setStatus(PaymentStatus.SUCCESS);
        } else if (pending) {
            tx.setStatus(PaymentStatus.PROCESSING);
        } else {
            tx.setStatus(PaymentStatus.FAILED);
            if (obj.has("data") && obj.get("data").has("message")) {
                tx.setFailureReason(obj.get("data").get("message").asText());
            }
        }
    }

    // =========================================================================
    // Refunds
    // =========================================================================

    @Transactional
    public RefundResponse initiateRefund(RefundRequest req) {
        PaymentTransaction tx = transactionRepo.findById(req.getTransactionId())
                .orElseThrow(() -> new NoSuchElementException("Transaction not found: " + req.getTransactionId()));

        if (tx.getStatus() != PaymentStatus.SUCCESS && tx.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new IllegalStateException("Refunds only allowed for SUCCESS or PARTIALLY_REFUNDED");
        }

        BigDecimal alreadyRefunded = refundRepo.sumAmountByTransactionAndStatus(tx, RefundStatus.SUCCESS);
        if (alreadyRefunded.add(req.getAmount()).compareTo(tx.getAmount()) > 0) {
            throw new IllegalArgumentException("Refund exceeds remaining amount");
        }

        PaymentProvider provider = tx.getMethodConfig().getProvider();
        long refundCents = req.getAmount().multiply(BigDecimal.valueOf(100)).longValue();

        // Use numeric paymob_transaction_id for refund call
        String providerRefundId = paymobClient.refund(
                provider.getSecretKey(), provider.getBaseUrl(),
                tx.getPaymobTransactionId().toString(), refundCents);

        PaymentRefund refund = new PaymentRefund();
        refund.setTransaction(tx);
        refund.setAmount(req.getAmount());
        refund.setAmountCents(refundCents);
        refund.setCurrency(tx.getCurrency());
        refund.setReason(req.getReason());
        refund.setStatus(RefundStatus.SUCCESS);
        refund.setProviderRefundId(providerRefundId);
        refund.setRefundedBy(req.getRefundedBy());
        refund = refundRepo.save(refund);

        BigDecimal totalRefunded = alreadyRefunded.add(req.getAmount());
        tx.setStatus(totalRefunded.compareTo(tx.getAmount()) >= 0 ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED);
        transactionRepo.save(tx);

        return toRefundResponse(refund);
    }

    // =========================================================================
    // Queries & Mappers
    // =========================================================================

    public TransactionResponse getTransaction(UUID transactionId) {
        PaymentTransaction tx = transactionRepo.findById(transactionId).orElseThrow();
        return toTransactionResponse(tx);
    }

    public List<TransactionResponse> getTransactionsByOrder(UUID appOrderId) {
        return transactionRepo.findAllByAppOrderId(appOrderId).stream().map(this::toTransactionResponse).toList();
    }

    private String buildOrderMetadata(InitiatePaymentRequest req) {
        try {
            ObjectNode meta = objectMapper.createObjectNode();
            if (req.getAddressId() != null) meta.put("addressId", req.getAddressId().toString());
            if (req.getShippingFee() != null) meta.put("shippingFee", req.getShippingFee().toPlainString());
            if (req.getDeliveryMethod() != null) meta.put("deliveryMethod", req.getDeliveryMethod());
            return objectMapper.writeValueAsString(meta);
        } catch (Exception e) { return null; }
    }

    private ObjectNode buildBillingData(InitiatePaymentRequest req, UserAddress address) {
        String[] nameParts = req.getBillingName() != null ? req.getBillingName().split(" ", 2) : new String[]{"NA", "NA"};
        ObjectNode n = objectMapper.createObjectNode();
        n.put("first_name", nameParts[0]);
        n.put("last_name", nameParts.length > 1 ? nameParts[1] : "NA");
        n.put("phone_number", req.getCustomerPhone() != null ? req.getCustomerPhone() : (address != null ? address.getPhoneNumber() : "NA"));
        n.put("apartment", req.getBillingApartment() != null ? req.getBillingApartment() : "NA");
        n.put("floor", req.getBillingFloor() != null ? req.getBillingFloor() : "NA");
        n.put("street", req.getBillingStreet() != null ? req.getBillingStreet() : (address != null ? address.getAddressLine1() : "NA"));
        n.put("building", req.getBillingBuilding() != null ? req.getBillingBuilding() : "NA");
        n.put("city", req.getBillingCity() != null ? req.getBillingCity() : (address != null ? address.getCity() : "NA"));
        n.put("country", req.getBillingCountry() != null ? req.getBillingCountry() : (address != null ? address.getCountry() : "AE"));
        n.put("state", req.getBillingState() != null ? req.getBillingState() : (address != null ? address.getState() : "NA"));
        n.put("postal_code", req.getBillingPostalCode() != null ? req.getBillingPostalCode() : (address != null ? address.getPostalCode() : "NA"));
        n.put("email", req.getCustomerEmail() != null ? req.getCustomerEmail() : "NA");
        return n;
    }

    private PaymentInitiatedResponse buildInitiatedResponse(PaymentTransaction tx, String clientSecret, String checkoutUrl) {
        PaymentInitiatedResponse res = new PaymentInitiatedResponse();
        res.setTransactionId(tx.getId());
        res.setMethodType(tx.getMethodType());
        res.setAmount(tx.getAmount());
        res.setCurrency(tx.getCurrency());
        res.setClientSecret(clientSecret);
        res.setCheckoutUrl(checkoutUrl);
        return res;
    }

    private boolean validateHmac(String rawPayload, String receivedHmac, String hmacSecret) {
        return PaymobHmacValidator.validate(rawPayload, receivedHmac, hmacSecret);
    }

    private boolean isTerminal(PaymentStatus status) {
        return status == PaymentStatus.SUCCESS || status == PaymentStatus.FAILED || status == PaymentStatus.CANCELLED 
                || status == PaymentStatus.REFUNDED || status == PaymentStatus.PARTIALLY_REFUNDED;
    }

    private TransactionResponse toTransactionResponse(PaymentTransaction tx) {
        TransactionResponse res = new TransactionResponse();
        res.setId(tx.getId());
        res.setAppOrderId(tx.getAppOrderId());
        res.setMethodType(tx.getMethodType());
        res.setAmount(tx.getAmount());
        res.setAmountCents(tx.getAmountCents());
        res.setCurrency(tx.getCurrency());
        res.setStatus(tx.getStatus());
        res.setPaymobTransactionId(tx.getPaymobTransactionId() != null ? tx.getPaymobTransactionId().toString() : null);
        res.setFailureReason(tx.getFailureReason());
        res.setFailureCode(tx.getFailureCode());
        res.setCreatedAt(tx.getCreatedAt());
        res.setUpdatedAt(tx.getUpdatedAt());
        return res;
    }

    private RefundResponse toRefundResponse(PaymentRefund refund) {
        RefundResponse res = new RefundResponse();
        res.setId(refund.getId());
        res.setTransactionId(refund.getTransaction().getId());
        res.setAmount(refund.getAmount());
        res.setAmountCents(refund.getAmountCents());
        res.setCurrency(refund.getCurrency());
        res.setStatus(refund.getStatus());
        res.setReason(refund.getReason());
        res.setProviderRefundId(refund.getProviderRefundId());
        res.setRefundedBy(refund.getRefundedBy());
        res.setCreatedAt(refund.getCreatedAt());
        return res;
    }
}
