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
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);


    private final PaymentProviderRepository providerRepo;
    private final PaymentMethodConfigRepository methodConfigRepo;
    private final PaymentProviderOrderRepository providerOrderRepo;
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

    public PaymentService(
            PaymentProviderRepository providerRepo,
            PaymentMethodConfigRepository methodConfigRepo,
            PaymentProviderOrderRepository providerOrderRepo,
            PaymentTransactionRepository transactionRepo,
            PaymentWebhookEventRepository webhookEventRepo,
            PaymentRefundRepository refundRepo,
            PaymobClient paymobClient,
            ObjectMapper objectMapper,
            UserProfileService userProfileService,
            UserAddressRepository addressRepo,
            ApplicationEventPublisher eventPublisher,
            CurrencyExchangeService currencyExchangeService,
            com.buyology.ecommerce.payment.config.PaymobProperties paymobProperties) {
        this.providerRepo = providerRepo;
        this.methodConfigRepo = methodConfigRepo;
        this.providerOrderRepo = providerOrderRepo;
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
    }

    // =========================================================================
    // Initiate payment — Paymob Intention API (v2, single-call flow)
    // =========================================================================

    @Transactional
    public PaymentInitiatedResponse initiatePayment(InitiatePaymentRequest req) {
        // Guard: user must have firstName, lastName, phoneNumber, and at least one address
        userProfileService.checkPaymentReadiness(req.getCustomerId());

        PaymentProvider provider = providerRepo.findFirstByIsActiveTrue()
                .orElseThrow(() -> new IllegalStateException("No active payment provider configured"));

        PaymentMethodConfig config = methodConfigRepo
                .findByProviderAndMethodTypeAndIsActiveTrue(provider, req.getMethodType())
                .orElseThrow(() -> new IllegalStateException(
                        "No active config for method: " + req.getMethodType()));

        // Paymob UAE Integration IDs usually ONLY support AED.
        // We convert the amount to AED if the incoming currency is different.
        String targetCurrency = "AED";
        BigDecimal convertedAmount = currencyExchangeService.convert(req.getAmount(), req.getCurrency(), targetCurrency);

        long amountCents = convertedAmount
                .multiply(BigDecimal.valueOf(100))
                .longValue();

        // Build billing_data node — fall back to saved address when frontend omits billing fields
        UserAddress address = req.getAddressId() != null
                ? addressRepo.findById(req.getAddressId()).orElse(null)
                : null;
        ObjectNode billingData = buildBillingData(req, address);

        // Build customer node
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

        // Build items array (empty line item representing the order total)
        ArrayNode items = objectMapper.createArrayNode();
        ObjectNode item = objectMapper.createObjectNode();
        item.put("name", "Order " + req.getAppOrderId());
        item.put("amount", amountCents);
        item.put("description", "Order payment");
        item.put("quantity", 1);
        items.add(item);

        int integrationId = Integer.parseInt(config.getIntegrationId());
        // Use cartId as the reference base (appOrderId may be null in cart-first flow)
        UUID referenceId = req.getCartId() != null ? req.getCartId() : req.getAppOrderId();
        // Unique per attempt — Paymob rejects duplicate special_reference values
        String specialReference = referenceId + "-" + UUID.randomUUID().toString().substring(0, 8);

        // Single API call — replaces the old authenticate → createOrder → generatePaymentKey chain
        String redirectionUrl = req.getRedirectionUrl() != null && !req.getRedirectionUrl().isBlank()
                ? req.getRedirectionUrl()
                : paymobProperties.getRedirectionUrl();

        // 1. Create and commit the pending transaction state FIRST
        // This ensures the record exists in the DB before the Paymob API is called,
        // so if the webhook arrives instantly, it can be matched.
        PaymentTransaction tx = savePendingTransaction(req, provider, config, convertedAmount, amountCents, targetCurrency);

        // 2. Call Paymob API
        PaymobClient.IntentionResult intention = paymobClient.createIntention(
                provider.getSecretKey(), provider.getBaseUrl(),
                amountCents, targetCurrency,
                integrationId, specialReference,
                billingData, customer, items,
                provider.getNotificationUrl(),
                redirectionUrl);

        log.info("[PAYMENT] Created Paymob Intention: id={}, clientSecret={}", intention.intentionId(), intention.clientSecret());

        // 3. Update the transaction with the provider IDs in a second transaction
        return finalizeTransactionWithProvider(tx.getId(), intention);
    }

    /**
     * Creates a pending transaction record and commits it immediately.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentTransaction savePendingTransaction(InitiatePaymentRequest req, 
                                                     PaymentProvider provider, 
                                                     PaymentMethodConfig config,
                                                     BigDecimal amount,
                                                     long amountCents,
                                                     String currency) {
        // Create an initial provider order record with a placeholder ID
        // The column is NOT NULL and UNIQUE, so we use a UUID-based placeholder.
        PaymentProviderOrder providerOrder = new PaymentProviderOrder();
        providerOrder.setAppOrderId(req.getAppOrderId());
        providerOrder.setProvider(provider);
        providerOrder.setAmountCents(amountCents);
        providerOrder.setCurrency(currency);
        providerOrder.setProviderOrderId("PENDING-" + UUID.randomUUID().toString());
        providerOrder = providerOrderRepo.save(providerOrder);

        PaymentTransaction tx = new PaymentTransaction();
        tx.setAppOrderId(req.getAppOrderId());
        tx.setCartId(req.getCartId());
        tx.setProviderOrder(providerOrder);
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
        log.info("[PAYMENT] Committed PENDING transaction state: id={}, cartId={}, tempProviderId={}", 
                 tx.getId(), tx.getCartId(), providerOrder.getProviderOrderId());
        return tx;
    }

    /**
     * Updates the transaction with Paymob intention IDs and commits.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentInitiatedResponse finalizeTransactionWithProvider(UUID transactionId, 
                                                                   PaymobClient.IntentionResult intention) {
        PaymentTransaction tx = transactionRepo.findById(transactionId)
                .orElseThrow(() -> new NoSuchElementException("Transaction not found during finalization: " + transactionId));
        
        PaymentProviderOrder po = tx.getProviderOrder();
        po.setProviderOrderId(intention.intentionId());
        providerOrderRepo.save(po);

        tx.setPaymentKeyToken(intention.clientSecret());
        transactionRepo.save(tx);

        log.info("[PAYMENT] Finalized transaction with provider IDs: id={}, providerOrderId={}", 
                 tx.getId(), intention.intentionId());

        String checkoutUrl = po.getProvider().getBaseUrl()
                + "/unifiedcheckout/?publicKey=" + po.getProvider().getPublicKey()
                + "&clientSecret=" + intention.clientSecret();

        return buildInitiatedResponse(tx, intention.clientSecret(), checkoutUrl);
    }

    // =========================================================================
    // Webhook handling — store first, process second
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
            throw new RuntimeException("Invalid webhook payload JSON", e);
        }

        // Paymob UAE wraps the transaction in "obj"
        JsonNode dataNode = root.has("obj") ? root.get("obj") 
                         : root.has("transaction") ? root.get("transaction") 
                         : root;

        String providerTxnId = extractProviderTxnId(root);
        log.info("[WEBHOOK] Received notification. providerTxnId={}, hmacValid={}", providerTxnId, hmacValid);

        // Idempotency: if already processed successfully, return immediately
        if (providerTxnId != null &&
                webhookEventRepo.findFirstByProviderTxnIdAndProcessedTrue(providerTxnId).isPresent()) {
            log.info("[WEBHOOK] Transaction {} already processed. Skipping.", providerTxnId);
            return;
        }

        // Look up the matching transaction
        PaymentTransaction transaction = null;
        if (providerTxnId != null) {
            transaction = transactionRepo.findByProviderTransactionId(providerTxnId).orElse(null);
        }
        
        if (transaction == null) {
            log.info("[WEBHOOK] Could not match via providerTxnId {}, attempting to resolve from payload...", providerTxnId);
            transaction = resolveTransactionFromPayload(root);
        }

        if (transaction != null) {
            log.info("[WEBHOOK] Matched notification to PaymentTransaction: id={}, appOrderId={}", 
                     transaction.getId(), transaction.getAppOrderId());
        }

        // --- Store raw event ---
        PaymentWebhookEvent event = new PaymentWebhookEvent();
        event.setProvider(provider);
        event.setTransaction(transaction);
        event.setProviderTxnId(providerTxnId);
        event.setHmacValid(hmacValid);
        event.setPayload(rawPayload);
        event.setProcessed(false);
        event = webhookEventRepo.save(event);

        if (!hmacValid) {
            log.warn("[HMAC] Signature validation failed for txn {} — webhook rejected", providerTxnId);
            event.setError("HMAC validation failed");
            webhookEventRepo.save(event);
            return;
        }

        if (transaction == null) {
            log.warn("[WEBHOOK] Could not match notification to any local transaction. txnId={}", providerTxnId);
            event.setError("No matching transaction found");
            webhookEventRepo.save(event);
            return;
        }

        if (isTerminal(transaction.getStatus())) {
            log.info("[WEBHOOK] Transaction {} already in terminal state {}. Stopping.", providerTxnId, transaction.getStatus());
            event.setProcessed(true);
            event.setProcessedAt(Instant.now());
            webhookEventRepo.save(event);
            return;
        }

        // Process
        try {
            applyWebhookToTransaction(transaction, dataNode, providerTxnId);
            transactionRepo.save(transaction);

            log.info("[WEBHOOK] Transaction {} updated to status {}.", providerTxnId, transaction.getStatus());

            if (transaction.getStatus() == PaymentStatus.SUCCESS) {
                log.info("[WEBHOOK] Payment successful. Publishing PaymentSucceededEvent for order/cart.");
                eventPublisher.publishEvent(
                        new PaymentSucceededEvent(transaction.getAppOrderId(), transaction.getId()));
            }

            event.setProcessed(true);
            event.setProcessedAt(Instant.now());
            event.setTransaction(transaction);
        } catch (Exception e) {
            log.error("[WEBHOOK] Error processing transaction {}", providerTxnId, e);
            event.setError(e.getMessage());
        }

        webhookEventRepo.save(event);
    }

    // =========================================================================
    // Refunds
    // =========================================================================

    @Transactional
    public RefundResponse initiateRefund(RefundRequest req) {
        PaymentTransaction tx = transactionRepo.findById(req.getTransactionId())
                .orElseThrow(() -> new NoSuchElementException(
                        "Transaction not found: " + req.getTransactionId()));

        if (tx.getStatus() != PaymentStatus.SUCCESS &&
                tx.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new IllegalStateException(
                    "Refunds can only be initiated on SUCCESS or PARTIALLY_REFUNDED transactions");
        }

        // Partial refund guard — enforce in service, not DB trigger, so it is testable
        BigDecimal alreadyRefunded = refundRepo.sumAmountByTransactionAndStatus(tx, RefundStatus.SUCCESS);
        if (alreadyRefunded.add(req.getAmount()).compareTo(tx.getAmount()) > 0) {
            throw new IllegalArgumentException(
                    "Refund amount exceeds remaining refundable amount. " +
                    "Original: " + tx.getAmount() +
                    ", already refunded: " + alreadyRefunded +
                    ", requested: " + req.getAmount());
        }

        PaymentProvider provider = tx.getMethodConfig().getProvider();

        long refundCents = req.getAmount()
                .multiply(BigDecimal.valueOf(100))
                .longValue();

        String providerRefundId = paymobClient.refund(
                provider.getSecretKey(), provider.getBaseUrl(),
                tx.getProviderTransactionId(), refundCents);

        PaymentRefund refund = new PaymentRefund();
        refund.setTransaction(tx);
        refund.setAmount(req.getAmount());
        refund.setAmountCents(refundCents);
        refund.setCurrency(tx.getCurrency());
        refund.setReason(req.getReason());
        refund.setNotes(req.getNotes());
        refund.setStatus(RefundStatus.SUCCESS);
        refund.setProviderRefundId(providerRefundId);
        refund.setRefundedBy(req.getRefundedBy());
        refund = refundRepo.save(refund);

        // Update transaction status
        BigDecimal totalRefunded = alreadyRefunded.add(req.getAmount());
        if (totalRefunded.compareTo(tx.getAmount()) >= 0) {
            tx.setStatus(PaymentStatus.REFUNDED);
        } else {
            tx.setStatus(PaymentStatus.PARTIALLY_REFUNDED);
        }
        transactionRepo.save(tx);

        return toRefundResponse(refund);
    }

    // =========================================================================
    // Queries
    // =========================================================================

    public TransactionResponse getTransaction(UUID transactionId) {
        PaymentTransaction tx = transactionRepo.findById(transactionId)
                .orElseThrow(() -> new NoSuchElementException("Transaction not found: " + transactionId));
        return toTransactionResponse(tx);
    }

    public List<TransactionResponse> getTransactionsByOrder(UUID appOrderId) {
        return transactionRepo.findAllByAppOrderId(appOrderId)
                .stream()
                .map(this::toTransactionResponse)
                .toList();
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private String buildOrderMetadata(InitiatePaymentRequest req) {
        try {
            ObjectNode meta = objectMapper.createObjectNode();
            if (req.getAddressId() != null) meta.put("addressId", req.getAddressId().toString());
            if (req.getShippingFee() != null) meta.put("shippingFee", req.getShippingFee().toPlainString());
            if (req.getDeliveryMethod() != null) meta.put("deliveryMethod", req.getDeliveryMethod());
            return objectMapper.writeValueAsString(meta);
        } catch (Exception e) {
            return null;
        }
    }

    private ObjectNode buildBillingData(InitiatePaymentRequest req, UserAddress address) {
        String[] nameParts = req.getBillingName() != null
                ? req.getBillingName().split(" ", 2)
                : new String[]{"NA", "NA"};

        ObjectNode n = objectMapper.createObjectNode();
        n.put("first_name", nameParts[0]);
        n.put("last_name", nameParts.length > 1 ? nameParts[1] : "NA");
        n.put("phone_number", req.getCustomerPhone() != null ? req.getCustomerPhone()
                : address != null && address.getPhoneNumber() != null ? address.getPhoneNumber() : "NA");
        n.put("apartment", req.getBillingApartment() != null ? req.getBillingApartment() : "NA");
        n.put("floor", req.getBillingFloor() != null ? req.getBillingFloor() : "NA");
        n.put("street", req.getBillingStreet() != null ? req.getBillingStreet()
                : address != null && address.getAddressLine1() != null ? address.getAddressLine1() : "NA");
        n.put("building", req.getBillingBuilding() != null ? req.getBillingBuilding() : "NA");
        n.put("city", req.getBillingCity() != null ? req.getBillingCity()
                : address != null && address.getCity() != null ? address.getCity() : "NA");
        n.put("country", req.getBillingCountry() != null ? req.getBillingCountry()
                : address != null && address.getCountry() != null ? address.getCountry() : "AE");
        n.put("state", req.getBillingState() != null ? req.getBillingState()
                : address != null && address.getState() != null ? address.getState() : "NA");
        n.put("postal_code", req.getBillingPostalCode() != null ? req.getBillingPostalCode()
                : address != null && address.getPostalCode() != null ? address.getPostalCode() : "NA");
        n.put("email", req.getCustomerEmail() != null ? req.getCustomerEmail() : "NA");
        return n;
    }

    private PaymentInitiatedResponse buildInitiatedResponse(PaymentTransaction tx,
                                                             String clientSecret,
                                                             String checkoutUrl) {
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

    private String extractProviderTxnId(JsonNode payload) {
        JsonNode obj = payload.has("obj") ? payload.get("obj")
                     : payload.has("transaction") ? payload.get("transaction")
                     : payload;
        JsonNode idNode = obj.get("id");
        return idNode != null && !idNode.isNull() ? idNode.asText() : null;
    }

    private PaymentTransaction resolveTransactionFromPayload(JsonNode payload) {
        try {
            // 1. Try root intention_id (Paymob Intention API v2 often puts it at root)
            String intentionId = null;
            if (payload.has("intention_id") && !payload.get("intention_id").isNull()) {
                intentionId = payload.get("intention_id").asText();
                log.info("[WEBHOOK] Found intentionId at root: {}", intentionId);
            }

            // 2. Try obj.order.id or obj.intention.id
            JsonNode data = payload.has("obj") ? payload.get("obj") : payload;
            if (intentionId == null) {
                if (data.has("order") && data.get("order").has("id")) {
                    intentionId = data.get("order").get("id").asText();
                    log.info("[WEBHOOK] Found intentionId in obj.order.id: {}", intentionId);
                } else if (data.has("intention") && data.get("intention").has("id")) {
                    intentionId = data.get("intention").get("id").asText();
                    log.info("[WEBHOOK] Found intentionId in obj.intention.id: {}", intentionId);
                }
            }

            if (intentionId != null) {
                final String finalId = intentionId;
                log.info("[WEBHOOK] Searching for PaymentProviderOrder with providerOrderId: {}", finalId);
                PaymentTransaction tx = providerOrderRepo.findByProviderOrderId(finalId)
                        .flatMap(po -> {
                            log.info("[WEBHOOK] Found PaymentProviderOrder: id={}, providerOrderId={}", po.getId(), po.getProviderOrderId());
                            return transactionRepo.findFirstByProviderOrderAndStatusIn(
                                po, List.of(PaymentStatus.PENDING, PaymentStatus.PROCESSING));
                        })
                        .orElse(null);
                if (tx != null) {
                    log.info("[WEBHOOK] Matched via intentionId {} to transaction: id={}", intentionId, tx.getId());
                    return tx;
                }
                log.warn("[WEBHOOK] intentionId {} found but no PENDING transaction matched", intentionId);
            }

            // 3. Last resort: special_reference (mapped to cartId or appOrderId)
            // specialReference format: <uuid>-<short_id>
            if (data.has("special_reference") && !data.get("special_reference").isNull()) {
                String specRef = data.get("special_reference").asText();
                log.info("[WEBHOOK] Attempting resolution via special_reference: {}", specRef);
                try {
                    String uuidPart = specRef.contains("-") ? specRef.substring(0, specRef.lastIndexOf("-")) : specRef;
                    UUID refId = UUID.fromString(uuidPart);
                    // Could be cartId or appOrderId
                    PaymentTransaction tx = transactionRepo.findFirstByCartIdAndStatusIn(refId, List.of(PaymentStatus.PENDING, PaymentStatus.PROCESSING))
                            .or(() -> transactionRepo.findFirstByAppOrderIdAndStatusIn(refId, List.of(PaymentStatus.PENDING, PaymentStatus.PROCESSING)))
                            .orElse(null);
                    if (tx != null) {
                        log.info("[WEBHOOK] Matched via special_reference {} to transaction: id={}", specRef, tx.getId());
                        return tx;
                    }
                } catch (Exception e) {
                    log.warn("[WEBHOOK] Failed to parse UUID from special_reference: {}", specRef);
                }
            }

            return null;
        } catch (Exception e) {
            log.error("[WEBHOOK] Error resolving transaction from payload", e);
            return null;
        }
    }

    private void applyWebhookToTransaction(PaymentTransaction tx, JsonNode dataNode, String providerTxnId) {
        tx.setProviderTransactionId(providerTxnId);
        tx.setStatus(PaymentStatus.PROCESSING);

        boolean success = dataNode.has("success") && dataNode.get("success").asBoolean();
        boolean pending = dataNode.has("pending") && dataNode.get("pending").asBoolean();

        if (success) {
            tx.setStatus(PaymentStatus.SUCCESS);
        } else if (!pending) {
            tx.setStatus(PaymentStatus.FAILED);
            if (dataNode.has("data") && dataNode.get("data").has("message")) {
                tx.setFailureReason(dataNode.get("data").get("message").asText());
            }
        }
    }

    private boolean isTerminal(PaymentStatus status) {
        return status == PaymentStatus.SUCCESS
                || status == PaymentStatus.FAILED
                || status == PaymentStatus.CANCELLED
                || status == PaymentStatus.REFUNDED
                || status == PaymentStatus.PARTIALLY_REFUNDED;
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
        res.setProviderTransactionId(tx.getProviderTransactionId());
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
