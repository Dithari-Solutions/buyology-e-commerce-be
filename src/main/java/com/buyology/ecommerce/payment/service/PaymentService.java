package com.buyology.ecommerce.payment.service;

import com.buyology.ecommerce.order.event.PaymentSucceededEvent;
import com.buyology.ecommerce.order.event.PaymentFailedEvent;
import com.buyology.ecommerce.payment.domain.*;
import com.buyology.ecommerce.payment.dto.*;
import com.buyology.ecommerce.payment.enums.PaymentMethodType;
import com.buyology.ecommerce.payment.enums.PaymentPurpose;
import com.buyology.ecommerce.payment.enums.PaymentStatus;
import com.buyology.ecommerce.payment.enums.RefundStatus;
import com.buyology.ecommerce.payment.event.CourierFeePaidEvent;
import com.buyology.ecommerce.payment.repository.*;
import com.buyology.ecommerce.currency.service.CurrencyExchangeService;
import com.buyology.ecommerce.common.utils.SecurityUtils;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentProviderRepository providerRepo;
    private final PaymentMethodConfigRepository methodConfigRepo;
    private final PaymentTransactionRepository transactionRepo;
    private final PaymentWebhookEventRepository webhookEventRepo;
    private final ProcessedWebhookEventRepository processedWebhookEventRepo;
    private final PaymentRefundRepository refundRepo;
    private final PaymobClient paymobClient;
    private final ObjectMapper objectMapper;
    private final UserProfileService userProfileService;
    private final UserAddressRepository addressRepo;
    private final ApplicationEventPublisher eventPublisher;
    private final CurrencyExchangeService currencyExchangeService;
    private final com.buyology.ecommerce.payment.config.PaymobProperties paymobProperties;
    private final org.springframework.beans.factory.ObjectProvider<com.buyology.ecommerce.membership.service.CreditPaybackService> creditPaybackProvider;
    private final org.springframework.beans.factory.ObjectProvider<com.buyology.ecommerce.order.repository.OrderRepository> orderRepoProvider;
    private final com.buyology.ecommerce.auth.repository.AuthCredentialRepository authCredentialRepo;

    public PaymentService(
            PaymentProviderRepository providerRepo,
            PaymentMethodConfigRepository methodConfigRepo,
            PaymentTransactionRepository transactionRepo,
            PaymentWebhookEventRepository webhookEventRepo,
            ProcessedWebhookEventRepository processedWebhookEventRepo,
            PaymentRefundRepository refundRepo,
            PaymobClient paymobClient,
            ObjectMapper objectMapper,
            UserProfileService userProfileService,
            UserAddressRepository addressRepo,
            ApplicationEventPublisher eventPublisher,
            CurrencyExchangeService currencyExchangeService,
            com.buyology.ecommerce.payment.config.PaymobProperties paymobProperties,
            org.springframework.beans.factory.ObjectProvider<com.buyology.ecommerce.membership.service.CreditPaybackService> creditPaybackProvider,
            org.springframework.beans.factory.ObjectProvider<com.buyology.ecommerce.order.repository.OrderRepository> orderRepoProvider,
            com.buyology.ecommerce.auth.repository.AuthCredentialRepository authCredentialRepo) {
        this.providerRepo = providerRepo;
        this.methodConfigRepo = methodConfigRepo;
        this.transactionRepo = transactionRepo;
        this.webhookEventRepo = webhookEventRepo;
        this.processedWebhookEventRepo = processedWebhookEventRepo;
        this.refundRepo = refundRepo;
        this.paymobClient = paymobClient;
        this.objectMapper = objectMapper;
        this.userProfileService = userProfileService;
        this.addressRepo = addressRepo;
        this.eventPublisher = eventPublisher;
        this.currencyExchangeService = currencyExchangeService;
        this.paymobProperties = paymobProperties;
        this.creditPaybackProvider = creditPaybackProvider;
        this.orderRepoProvider = orderRepoProvider;
        this.authCredentialRepo = authCredentialRepo;
    }

    /**
     * Resolves the {@code users.id} that owns a given {@code auth_credentials.id} and
     * asserts it is the authenticated principal (admins bypass). {@code customerId}
     * stored on transactions is an auth_credentials.id, whereas the JWT principal is a
     * users.id — this bridges the two for ownership checks.
     */
    private void requireOwnsByCredentialId(UUID authCredentialId) {
        if (authCredentialId == null) {
            throw new org.springframework.security.access.AccessDeniedException("Not allowed");
        }
        UUID ownerUserId = authCredentialRepo.findById(authCredentialId)
                .map(c -> c.getUserId())
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Not allowed"));
        SecurityUtils.requireSelfOrAdmin(ownerUserId);
    }

    // =========================================================================
    // Initiate payment — Paymob Intention API (v2, single-call flow)
    // =========================================================================

    @Transactional
    public PaymentInitiatedResponse initiatePayment(InitiatePaymentRequest req) {
        // customerId from the client is an auth_credentials.id. checkPaymentReadiness
        // resolves it to a user and asserts it belongs to the authenticated principal
        // (its findUser does requireSelfOrAdmin), so paying on another user's behalf is
        // already blocked — we must NOT overwrite it with the principal's users.id, which
        // findUser cannot resolve.
        UUID currentUserId = SecurityUtils.currentUserId();
        userProfileService.checkPaymentReadiness(req.getCustomerId());

        PaymentProvider provider = providerRepo.findFirstByIsActiveTrue()
                .orElseThrow(() -> new IllegalStateException("No active payment provider configured"));

        PaymentMethodConfig config = methodConfigRepo
                .findByProviderAndMethodTypeAndIsActiveTrue(provider, req.getMethodType())
                .orElseThrow(() -> new IllegalStateException(
                        "No active config for method: " + req.getMethodType()));

        String targetCurrency = "AED";

        // If the order has already had B2B credit applied, charge only the remainder.
        BigDecimal effectiveAmount = req.getAmount();
        if (req.getAppOrderId() != null) {
            var order = orderRepoProvider.getObject().findById(req.getAppOrderId()).orElse(null);
            // Server-side ownership + amount reconciliation: the client cannot pay for
            // someone else's order, nor pay less than the order's authoritative total.
            if (order != null) {
                if (order.getUserId() != null && !order.getUserId().equals(currentUserId)
                        && !SecurityUtils.isAdmin()) {
                    throw new org.springframework.security.access.AccessDeniedException(
                            "You are not allowed to pay for this order");
                }
                // Bind the stored transaction to the order's authoritative auth_credentials.id
                // so ownership reads (requireOwnsByCredentialId) resolve correctly regardless
                // of the customerId the client supplied. checkPaymentReadiness above already
                // validated the client value against the principal; this only corrects what we
                // persist, so a client sending its users.id can no longer 403 itself on read.
                if (order.getAuthCredentialId() != null) {
                    req.setCustomerId(order.getAuthCredentialId());
                }
                BigDecimal orderTotal = order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount();
                BigDecimal totalInReqCcy = order.getCurrency() != null
                        && order.getCurrency().equalsIgnoreCase(req.getCurrency())
                        ? orderTotal
                        : currencyExchangeService.convert(orderTotal, order.getCurrency(), req.getCurrency());
                // Allow a 1% tolerance for rounding / FX drift; reject gross underpayment.
                if (req.getAmount().compareTo(totalInReqCcy.multiply(new BigDecimal("0.99"))) < 0) {
                    throw new IllegalArgumentException(
                            "Payment amount does not match the order total");
                }
            }
            if (order != null && order.getCreditApplied() != null
                    && order.getCreditApplied().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal creditInRequestCcy = order.getCreditCurrency() != null
                        && order.getCreditCurrency().equalsIgnoreCase(req.getCurrency())
                        ? order.getCreditApplied()
                        : currencyExchangeService.convert(order.getCreditApplied(),
                                order.getCreditCurrency(), req.getCurrency());
                effectiveAmount = req.getAmount()
                        .subtract(creditInRequestCcy)
                        .max(BigDecimal.ZERO)
                        .setScale(2, java.math.RoundingMode.HALF_UP);
                if (effectiveAmount.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalStateException(
                            "Order is already fully settled by B2B credit; nothing to charge");
                }
                log.info("[PAYMENT] Credit-applied order {}: charging {} {} (was {} {} before credit of {} {})",
                        req.getAppOrderId(), effectiveAmount, req.getCurrency(),
                        req.getAmount(), req.getCurrency(),
                        order.getCreditApplied(), order.getCreditCurrency());
            }
        }

        BigDecimal convertedAmount = currencyExchangeService.convert(effectiveAmount, req.getCurrency(), targetCurrency);

        long amountCents = convertedAmount
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .longValueExact();

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

    /**
     * Re-initiate payment for an existing order still awaiting payment ("pay again"
     * from the orders page). Reuses {@link #initiatePayment} — which already binds the
     * payer to the principal, checks order ownership, and reconciles the amount.
     */
    @Transactional
    public PaymentInitiatedResponse repayOrder(UUID orderId, RepayOrderRequest req) {
        var order = orderRepoProvider.getObject().findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found: " + orderId));
        if (order.getStatus() != com.buyology.ecommerce.order.domain.enums.OrderStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("This order is not awaiting payment");
        }
        // Ownership is enforced inside initiatePayment (order.userId == principal).
        InitiatePaymentRequest ip = new InitiatePaymentRequest();
        ip.setAppOrderId(orderId);
        ip.setCartId(order.getCartId());
        // customerId is an auth_credentials.id (what checkPaymentReadiness resolves).
        ip.setCustomerId(order.getAuthCredentialId());
        ip.setMethodType(req.getMethodType());
        ip.setAmount(order.getTotalAmount());
        ip.setCurrency(order.getCurrency());
        ip.setCustomerEmail(req.getCustomerEmail());
        ip.setCustomerPhone(order.getRecipientPhone());
        ip.setBillingName(((order.getRecipientFirstName() == null ? "" : order.getRecipientFirstName())
                + " " + (order.getRecipientLastName() == null ? "" : order.getRecipientLastName())).trim());
        ip.setRedirectionUrl(req.getRedirectionUrl());
        return initiatePayment(ip);
    }

    /**
     * Initiate a STANDALONE Paymob charge for a refund courier return-pickup fee.
     * Unlike {@link #initiatePayment}, this is not tied to a cart or order: the
     * transaction is tagged {@code purpose = COURIER_RETURN_FEE} and linked to the
     * refund request, so the webhook routes its success to the refund flow (advancing
     * the request to COURIER_REQUESTED) and it is later reported as delivery-fee revenue.
     * Card only — instalment providers don't make sense for a small fee.
     */
    @Transactional
    public PaymentInitiatedResponse initiateCourierFeePayment(CourierFeeChargeRequest req) {
        // customerId is an auth_credentials.id — same readiness/ownership check as orders.
        userProfileService.checkPaymentReadiness(req.customerId());

        PaymentProvider provider = providerRepo.findFirstByIsActiveTrue()
                .orElseThrow(() -> new IllegalStateException("No active payment provider configured"));
        PaymentMethodConfig config = methodConfigRepo
                .findByProviderAndMethodTypeAndIsActiveTrue(provider, PaymentMethodType.CARD)
                .orElseThrow(() -> new IllegalStateException("No active card config for courier fee payment"));

        String targetCurrency = "AED";
        BigDecimal convertedAmount = currencyExchangeService.convert(req.amount(), req.currency(), targetCurrency);
        long amountCents = convertedAmount
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .longValueExact();

        ObjectNode billingData = buildCourierFeeBilling(req);

        ObjectNode customer = objectMapper.createObjectNode();
        customer.put("email", req.customerEmail() != null ? req.customerEmail() : "NA");
        String[] nameParts = req.billingName() != null ? req.billingName().split(" ", 2) : new String[]{"NA", "NA"};
        customer.put("first_name", nameParts[0]);
        customer.put("last_name", nameParts.length > 1 ? nameParts[1] : "NA");
        if (req.customerPhone() != null) {
            customer.put("phone_number", req.customerPhone());
        }

        ArrayNode items = objectMapper.createArrayNode();
        ObjectNode item = objectMapper.createObjectNode();
        item.put("name", "Courier return pickup fee");
        item.put("amount", amountCents);
        item.put("description", "Refund request " + req.refundRequestId());
        item.put("quantity", 1);
        items.add(item);

        int integrationId = Integer.parseInt(config.getIntegrationId());

        // Commit the PENDING transaction first (REQUIRES_NEW) so the row — and its
        // merchant_order_id — exists before we call Paymob; the webhook resolves on it.
        PaymentTransaction tx = savePendingCourierFeeTransaction(req, config, convertedAmount, amountCents, targetCurrency);

        String redirectionUrl = req.redirectionUrl() != null && !req.redirectionUrl().isBlank()
                ? req.redirectionUrl()
                : paymobProperties.getRedirectionUrl();

        PaymobClient.IntentionResult intention = paymobClient.createIntention(
                provider.getSecretKey(), provider.getBaseUrl(),
                amountCents, targetCurrency,
                integrationId, tx.getId().toString(),
                billingData, customer, items,
                provider.getNotificationUrl(),
                redirectionUrl);

        log.info("[COURIER-FEE] Created Paymob Intention for refund {}: tx={}, intention={}",
                req.refundRequestId(), tx.getId(), intention.intentionId());

        return finalizeTransactionWithProvider(tx.getId(), intention);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentTransaction savePendingCourierFeeTransaction(CourierFeeChargeRequest req,
                                                               PaymentMethodConfig config,
                                                               BigDecimal amount,
                                                               long amountCents,
                                                               String currency) {
        PaymentTransaction tx = new PaymentTransaction();
        tx.setPurpose(PaymentPurpose.COURIER_RETURN_FEE);
        tx.setRefundRequestId(req.refundRequestId());
        tx.setMethodConfig(config);
        tx.setMethodType(PaymentMethodType.CARD);
        tx.setAmount(amount);
        tx.setAmountCents(amountCents);
        tx.setCurrency(currency);
        tx.setStatus(PaymentStatus.PENDING);
        tx.setCustomerId(req.customerId());
        tx.setCustomerEmail(req.customerEmail());
        tx.setCustomerPhone(req.customerPhone());
        tx.setBillingName(req.billingName());
        tx = transactionRepo.save(tx);
        log.info("[COURIER-FEE] Committed PENDING courier-fee transaction: id={}, refundRequest={}",
                tx.getId(), req.refundRequestId());
        return tx;
    }

    private ObjectNode buildCourierFeeBilling(CourierFeeChargeRequest req) {
        String[] nameParts = req.billingName() != null ? req.billingName().split(" ", 2) : new String[]{"NA", "NA"};
        ObjectNode n = objectMapper.createObjectNode();
        n.put("first_name", nameParts[0]);
        n.put("last_name", nameParts.length > 1 ? nameParts[1] : "NA");
        n.put("phone_number", req.customerPhone() != null ? req.customerPhone() : "NA");
        n.put("apartment", "NA");
        n.put("floor", "NA");
        n.put("street", "NA");
        n.put("building", "NA");
        n.put("city", "NA");
        n.put("country", "AE");
        n.put("state", "NA");
        n.put("postal_code", "NA");
        n.put("email", req.customerEmail() != null ? req.customerEmail() : "NA");
        return n;
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

        // 0. VERIFY HMAC FIRST. An invalid signature is rejected immediately —
        //    before resolving any transaction, touching the idempotency ledger,
        //    or mutating any state. (Audit-log the rejection, then abort.)
        boolean hmacValid = validateHmac(rawPayload, receivedHmac, provider.getHmacSecret());
        if (!hmacValid) {
            log.warn("[WEBHOOK] Rejecting webhook with invalid HMAC; no state will be mutated");
            saveWebhookEvent(provider, null, null, false, rawPayload, "Invalid HMAC");
            return;
        }

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

        String maybeMoid = null;
        if (obj.has("order") && obj.get("order").has("merchant_order_id")) {
            maybeMoid = obj.get("order").get("merchant_order_id").asText();
        }

        // 1. TRUE IDEMPOTENCY (INSERT-first). Reserve this event in the ledger
        //    before doing any work. The unique key is the numeric Paymob
        //    transaction id when present, else the merchant_order_id. A
        //    duplicate/replayed delivery collides on the UNIQUE constraint and
        //    is skipped — protecting both the normal and CRED- branches from
        //    double-processing (e.g. double-credit on the payback flow).
        String eventKey = providerTxnIdStr != null ? providerTxnIdStr : maybeMoid;
        if (eventKey == null) {
            log.error("[WEBHOOK] No event key (txn id / merchant_order_id) in payload. raw={}", rawPayload);
            saveWebhookEvent(provider, null, null, true, rawPayload, "No event key");
            return;
        }
        if (!reserveEventKey(eventKey)) {
            log.info("[WEBHOOK] Duplicate/replayed event {} — already processed. Skipping.", eventKey);
            return;
        }

        // 2. B2B credit payback short-circuit
        // CreditPaybackService creates Paymob intentions with merchant_order_id="CRED-<usageId>"
        // and does NOT create a PaymentTransaction row. Detect that and route to the payback flow.
        if (maybeMoid != null && maybeMoid.startsWith(
                com.buyology.ecommerce.membership.service.CreditPaybackService.MERCHANT_ID_PREFIX)) {
            boolean success = obj.has("success") && obj.get("success").asBoolean();
            if (!success) {
                log.info("[WEBHOOK][CRED] Non-success webhook for {}, skipping", maybeMoid);
                saveWebhookEvent(provider, null, providerTxnIdStr, true, rawPayload, "non-success");
                return;
            }
            long amountCents = obj.has("amount_cents") ? obj.get("amount_cents").asLong() : 0L;
            BigDecimal amountAed = BigDecimal.valueOf(amountCents).movePointLeft(2);
            try {
                creditPaybackProvider.getObject().markPaid(maybeMoid, amountAed);
                saveWebhookEvent(provider, null, providerTxnIdStr, true, rawPayload, null);
            } catch (Exception e) {
                log.error("[WEBHOOK][CRED] markPaid failed for {}", maybeMoid, e);
                saveWebhookEvent(provider, null, providerTxnIdStr, true, rawPayload, e.getMessage());
                throw e;
            }
            return;
        }

        // 3. Resolve Transaction
        PaymentTransaction transaction = resolveTransactionFromPayload(root);

        if (transaction == null) {
            log.error("[WEBHOOK] Could not resolve transaction from payload. raw={}", rawPayload);
            saveWebhookEvent(provider, null, providerTxnIdStr, true, rawPayload, "Unresolved transaction");
            return;
        }

        log.info("[WEBHOOK] Matched to Transaction: id={}, currentStatus={}", transaction.getId(), transaction.getStatus());

        if (isTerminal(transaction.getStatus())) {
            log.info("[WEBHOOK] Transaction {} already terminal ({}). Skipping.", transaction.getId(), transaction.getStatus());
            saveWebhookEvent(provider, transaction, providerTxnIdStr, true, rawPayload, null);
            return;
        }

        // 4. Process status update
        try {
            applyWebhookToTransaction(transaction, obj, providerTxnId);
            transactionRepo.saveAndFlush(transaction);

            log.info("[WEBHOOK] Transaction {} updated to {}", transaction.getId(), transaction.getStatus());

            boolean isCourierFee = transaction.getPurpose() == PaymentPurpose.COURIER_RETURN_FEE;
            if (transaction.getStatus() == PaymentStatus.SUCCESS) {
                if (isCourierFee) {
                    log.info("[WEBHOOK] Courier return fee paid for refund {}. Publishing CourierFeePaidEvent.",
                            transaction.getRefundRequestId());
                    eventPublisher.publishEvent(new CourierFeePaidEvent(
                            transaction.getRefundRequestId(), transaction.getId(),
                            transaction.getAmount(), transaction.getCurrency()));
                } else {
                    log.info("[WEBHOOK] SUCCESS! Publishing PaymentSucceededEvent.");
                    eventPublisher.publishEvent(new PaymentSucceededEvent(transaction.getAppOrderId(), transaction.getId()));
                }
            } else if (transaction.getStatus() == PaymentStatus.FAILED
                    || transaction.getStatus() == PaymentStatus.CANCELLED) {
                if (isCourierFee) {
                    // No order to fail — the refund request simply stays COURIER_FEE_PENDING
                    // so the customer can retry the fee payment or switch to store drop-off.
                    log.info("[WEBHOOK] Courier return fee charge {} for refund {}.",
                            transaction.getStatus(), transaction.getRefundRequestId());
                } else {
                    log.info("[WEBHOOK] FAILED/CANCELLED. Publishing PaymentFailedEvent.");
                    eventPublisher.publishEvent(new PaymentFailedEvent(
                            transaction.getAppOrderId(),
                            transaction.getId(),
                            transaction.getFailureReason()));
                }
            }

            saveWebhookEvent(provider, transaction, providerTxnIdStr, hmacValid, rawPayload, null);
        } catch (Exception e) {
            log.error("[WEBHOOK] Error processing transaction {}", transaction.getId(), e);
            saveWebhookEvent(provider, transaction, providerTxnIdStr, hmacValid, rawPayload, e.getMessage());
            throw e;
        }
    }

    /**
     * Reconstructs a webhook-shaped Paymob payload from the flat REDIRECT ("transaction
     * response callback") query parameters, so a browser redirect can be confirmed
     * through the exact same {@link #handleWebhook} path as the server-to-server
     * webhook — a resilient fallback for when the webhook is delayed, blocked, or
     * misconfigured.
     *
     * Paymob signs the redirect params with the SAME HMAC scheme as the webhook, over
     * the same canonical field set, so the reconstructed {@code {"obj": {...}, "type":
     * "TRANSACTION"}} payload verifies against the redirect's {@code hmac}. handleWebhook
     * then applies the full tested path: HMAC verification, the INSERT-first idempotency
     * ledger, transaction resolution, status transition, and the underpayment guard.
     * Because the idempotency key is the Paymob transaction id — identical to the real
     * webhook's — this can never double-process: whichever of the redirect or the
     * webhook arrives first wins and the other is skipped.
     *
     * Returns {@code null} when the params are absent or unsigned; the caller must then
     * not attempt confirmation. This method is intentionally side-effect-free: the caller
     * invokes the proxied {@link #handleWebhook} with the result so that method's own
     * {@code @Transactional} boundary and AFTER_COMMIT event apply (calling handleWebhook
     * from inside this class would self-invoke and bypass the transactional proxy).
     */
    public String buildRedirectWebhookPayload(Map<String, String> params) {
        if (params == null) return null;
        String hmac = params.get("hmac");
        if (hmac == null || hmac.isBlank()) {
            log.warn("[REDIRECT-CONFIRM] Missing hmac in redirect params — ignoring");
            return null;
        }

        // Canonical scalar fields, carried flat in the redirect query string. We copy
        // them as strings; the HMAC validator and status logic both read them via
        // asText()/asBoolean(), which treat "true"/"false"/numeric strings correctly,
        // and Paymob signs these same string representations.
        ObjectNode obj = objectMapper.createObjectNode();
        String[] flatFields = {
                "amount_cents", "created_at", "currency", "error_occured",
                "has_parent_transaction", "id", "integration_id", "is_3d_secure",
                "is_auth", "is_capture", "is_refunded", "is_standalone_payment",
                "is_voided", "owner", "pending", "success"
        };
        for (String f : flatFields) {
            String v = params.get(f);
            if (v != null) obj.put(f, v);
        }

        // The HMAC concatenation and transaction resolution both read order.id; the
        // redirect carries it flat as "order".
        String orderId = params.get("order");
        if (orderId != null) {
            ObjectNode order = objectMapper.createObjectNode();
            order.put("id", orderId);
            obj.set("order", order);
        }

        // source_data.{pan,sub_type,type} participate in the HMAC.
        ObjectNode src = objectMapper.createObjectNode();
        if (params.get("source_data.pan") != null) src.put("pan", params.get("source_data.pan"));
        if (params.get("source_data.sub_type") != null) src.put("sub_type", params.get("source_data.sub_type"));
        if (params.get("source_data.type") != null) src.put("type", params.get("source_data.type"));
        obj.set("source_data", src);

        // data.message feeds the failure reason on declined transactions (not signed).
        if (params.get("data.message") != null) {
            ObjectNode data = objectMapper.createObjectNode();
            data.put("message", params.get("data.message"));
            obj.set("data", data);
        }

        ObjectNode root = objectMapper.createObjectNode();
        root.set("obj", obj);
        root.put("type", "TRANSACTION");

        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("[REDIRECT-CONFIRM] Failed to build payload from redirect params: {}", e.getMessage());
            return null;
        }
    }

    /**
     * INSERT-first idempotency guard. Attempts to claim {@code eventKey} in the
     * processed-events ledger. Returns {@code true} if the claim succeeded (this
     * is the first time we see the event), {@code false} if the row already
     * exists — i.e. the event is a duplicate/replay and must be skipped.
     *
     * The insert is flushed eagerly so the UNIQUE constraint is enforced now,
     * letting two concurrently-delivered copies serialize: the loser sees the
     * {@link DataIntegrityViolationException} and bails out.
     */
    private boolean reserveEventKey(String eventKey) {
        try {
            processedWebhookEventRepo.saveAndFlush(new ProcessedWebhookEvent(eventKey));
            return true;
        } catch (DataIntegrityViolationException dup) {
            return false;
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
        // Lock the transaction row so concurrent refunds serialize on the
        // read-check-write of refund state (prevents two refunds both passing).
        PaymentTransaction tx = transactionRepo.findWithLockById(req.getTransactionId())
                .orElseThrow(() -> new NoSuchElementException("Transaction not found: " + req.getTransactionId()));

        if (tx.getStatus() != PaymentStatus.SUCCESS && tx.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new IllegalStateException("Refunds only allowed for SUCCESS or PARTIALLY_REFUNDED");
        }

        BigDecimal alreadyRefunded = refundRepo.sumAmountByTransactionAndStatus(tx, RefundStatus.SUCCESS);
        if (alreadyRefunded.add(req.getAmount()).compareTo(tx.getAmount()) > 0) {
            throw new IllegalArgumentException("Refund exceeds remaining amount");
        }

        PaymentProvider provider = tx.getMethodConfig().getProvider();
        long refundCents = req.getAmount()
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .longValueExact();

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
        // Attribute the refund to the authenticated admin, not a client-supplied id.
        refund.setRefundedBy(SecurityUtils.currentUserIdOrNull() != null
                ? SecurityUtils.currentUserIdOrNull()
                : req.getRefundedBy());
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
        // tx.customerId is an auth_credentials.id; resolve to its owner and verify it's the caller.
        requireOwnsByCredentialId(tx.getCustomerId());
        return toTransactionResponse(tx);
    }

    public List<TransactionResponse> getTransactionsByOrder(UUID appOrderId) {
        List<PaymentTransaction> txs = transactionRepo.findAllByAppOrderId(appOrderId);
        // Enforce ownership: a customer may only read their own order's transactions.
        txs.forEach(tx -> requireOwnsByCredentialId(tx.getCustomerId()));
        return txs.stream().map(this::toTransactionResponse).toList();
    }

    /**
     * Resolve a transaction by its Paymob order id (the {@code order} param on the
     * redirect). Unlike {@link #getTransaction}, this performs NO ownership check —
     * it is only reachable from the HMAC-authenticated redirect-confirm flow, where
     * the shopper may have no valid session. The caller MUST verify the redirect HMAC
     * before invoking this (see {@link #verifyRedirectHmac}). Returns {@code null}
     * when the id is absent/non-numeric or no transaction matches.
     */
    @Transactional(readOnly = true)
    public TransactionResponse resolveByPaymobOrder(String paymobOrderId) {
        if (paymobOrderId == null || paymobOrderId.isBlank()) return null;
        try {
            Long poid = Long.valueOf(paymobOrderId.trim());
            return transactionRepo.findByPaymobOrderId(poid)
                    .map(this::toTransactionResponse)
                    .orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Verify a Paymob redirect HMAC against the active provider's secret, using the
     * already-reconstructed webhook-shaped payload. Lets the redirect-confirm flow
     * gate transaction disclosure on a valid signature, so an attacker can't read an
     * order's status by guessing a Paymob order id with a bogus HMAC.
     */
    public boolean verifyRedirectHmac(String payload, String receivedHmac) {
        PaymentProvider provider = providerRepo.findFirstByIsActiveTrue().orElse(null);
        if (provider == null) return false;
        return validateHmac(payload, receivedHmac, provider.getHmacSecret());
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
