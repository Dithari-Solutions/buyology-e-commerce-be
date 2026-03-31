package com.buyology.ecommerce.payment.service;

import com.buyology.ecommerce.order.event.PaymentSucceededEvent;
import com.buyology.ecommerce.payment.domain.*;
import com.buyology.ecommerce.payment.dto.*;
import com.buyology.ecommerce.payment.enums.PaymentMethodType;
import com.buyology.ecommerce.payment.enums.PaymentStatus;
import com.buyology.ecommerce.payment.enums.RefundStatus;
import com.buyology.ecommerce.payment.repository.*;
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
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
            ApplicationEventPublisher eventPublisher) {
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

        long amountCents = req.getAmount()
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
        PaymobClient.IntentionResult intention = paymobClient.createIntention(
                provider.getSecretKey(), provider.getBaseUrl(),
                amountCents, req.getCurrency(),
                integrationId, specialReference,
                billingData, customer, items,
                provider.getNotificationUrl());

        // Each intention creates a new provider order row (one per attempt)
        PaymentProviderOrder providerOrder = new PaymentProviderOrder();
        providerOrder.setAppOrderId(req.getAppOrderId());
        providerOrder.setProvider(provider);
        providerOrder.setProviderOrderId(intention.intentionId());
        providerOrder.setAmountCents(amountCents);
        providerOrder.setCurrency(req.getCurrency());
        providerOrder = providerOrderRepo.save(providerOrder);

        // Build metadata JSON to store address/delivery details needed for order creation
        String metadata = buildOrderMetadata(req);

        // Persist the transaction in PENDING state
        // paymentKeyToken is repurposed to store the clientSecret
        PaymentTransaction tx = new PaymentTransaction();
        tx.setAppOrderId(req.getAppOrderId());
        tx.setCartId(req.getCartId());
        tx.setProviderOrder(providerOrder);
        tx.setMethodConfig(config);
        tx.setMethodType(req.getMethodType());
        tx.setAmount(req.getAmount());
        tx.setAmountCents(amountCents);
        tx.setCurrency(req.getCurrency());
        tx.setStatus(PaymentStatus.PENDING);
        tx.setPaymentKeyToken(intention.clientSecret());
        tx.setCustomerId(req.getCustomerId());
        tx.setCustomerEmail(req.getCustomerEmail());
        tx.setCustomerPhone(req.getCustomerPhone());
        tx.setBillingName(req.getBillingName());
        tx.setMetadata(metadata);
        tx = transactionRepo.save(tx);

        // Unified Checkout URL — works for card, Tabby, and Tamara
        String checkoutUrl = provider.getBaseUrl()
                + "/unifiedcheckout/?publicKey=" + provider.getPublicKey()
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

        JsonNode payload;
        try {
            payload = objectMapper.readTree(rawPayload);
        } catch (Exception e) {
            throw new RuntimeException("Invalid webhook payload JSON", e);
        }

        String providerTxnId = extractProviderTxnId(payload);

        // Idempotency: if already processed successfully, return immediately
        if (providerTxnId != null &&
                webhookEventRepo.findFirstByProviderTxnIdAndProcessedTrue(providerTxnId).isPresent()) {
            return;
        }

        // Look up the matching transaction (may be NULL if not found yet)
        PaymentTransaction transaction = null;
        if (providerTxnId != null) {
            transaction = transactionRepo.findByProviderTransactionId(providerTxnId).orElse(null);
            if (transaction == null) {
                // Try matching by Paymob order + pending status
                transaction = resolveTransactionFromPayload(payload);
            }
        }

        // --- Store raw event before any processing ---
        PaymentWebhookEvent event = new PaymentWebhookEvent();
        event.setProvider(provider);
        event.setTransaction(transaction);
        event.setProviderTxnId(providerTxnId);
        event.setHmacValid(hmacValid);
        event.setPayload(rawPayload);
        event.setProcessed(false);
        event = webhookEventRepo.save(event);

        if (!hmacValid) {
            log.warn("[HMAC] Signature validation failed — webhook rejected");
            event.setError("HMAC validation failed");
            webhookEventRepo.save(event);
            return;
        }

        // If no matching transaction: cannot process yet
        if (transaction == null) {
            event.setError("No matching transaction found for provider_txn_id: " + providerTxnId);
            webhookEventRepo.save(event);
            return;
        }

        // If transaction is already in a terminal state: log and stop
        PaymentStatus currentStatus = transaction.getStatus();
        if (isTerminal(currentStatus)) {
            event.setProcessed(true);
            event.setProcessedAt(Instant.now());
            event.setError("Transaction already in terminal state: " + currentStatus);
            webhookEventRepo.save(event);
            return;
        }

        // Process the webhook
        try {
            applyWebhookToTransaction(transaction, payload, providerTxnId);
            transactionRepo.save(transaction);

            // Notify the order module: either transition an existing order to PAID,
            // or create a new order from the cart (cart-first flow).
            if (transaction.getStatus() == PaymentStatus.SUCCESS
                    && (transaction.getAppOrderId() != null || transaction.getCartId() != null)) {
                eventPublisher.publishEvent(
                        new PaymentSucceededEvent(transaction.getAppOrderId(), transaction.getId()));
            }

            event.setProcessed(true);
            event.setProcessedAt(Instant.now());
            event.setTransaction(transaction);
        } catch (Exception e) {
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

    /**
     * Validates the Paymob HMAC-SHA512 signature sent as a query parameter.
     * The HMAC is computed over a concatenation of specific transaction fields.
     * See Paymob docs for the exact field ordering.
     */
    private boolean validateHmac(String rawPayload, String receivedHmac, String hmacSecret) {
        try {
            JsonNode node = objectMapper.readTree(rawPayload);
            JsonNode obj = node.has("transaction") ? node.get("transaction") : node;

            // Paymob HMAC fields — order is fixed by the spec
            String[] fields = {
                "amount_cents", "created_at", "currency", "error_occured",
                "has_parent_transaction", "id", "integration_id", "is_3d_secure",
                "is_auth", "is_capture", "is_refunded", "is_standalone_payment",
                "is_voided", "order.id", "owner", "pending",
                "source_data.pan", "source_data.sub_type", "source_data.type", "success"
            };

            StringBuilder concat = new StringBuilder();
            for (String field : fields) {
                JsonNode value;
                if (field.contains(".")) {
                    String[] parts = field.split("\\.", 2);
                    JsonNode parent = obj.get(parts[0]);
                    value = parent != null ? parent.get(parts[1]) : null;
                } else {
                    value = obj.get(field);
                }
                concat.append(value != null ? value.asText() : "");
            }

            Mac mac = Mac.getInstance("HmacSHA512");
            // Paymob HMAC secret is used as a plain UTF-8 string (not hex-decoded)
            byte[] secretBytes = hmacSecret.getBytes(StandardCharsets.UTF_8);
            mac.init(new SecretKeySpec(secretBytes, "HmacSHA512"));
            byte[] hash = mac.doFinal(concat.toString().getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));

            String computed = hex.toString();
            return computed.equals(receivedHmac);
        } catch (Exception e) {
            return false;
        }
    }

    private String extractProviderTxnId(JsonNode payload) {
        JsonNode obj = payload.has("transaction") ? payload.get("transaction") : payload;
        JsonNode idNode = obj.get("id");
        return idNode != null && !idNode.isNull() ? idNode.asText() : null;
    }

    private PaymentTransaction resolveTransactionFromPayload(JsonNode payload) {
        try {
            // Intention API: intention ID is at payload.intention.id (e.g. pi_live_...)
            JsonNode intentionNode = payload.get("intention");
            if (intentionNode == null) return null;
            String intentionId = intentionNode.get("id").asText();

            return providerOrderRepo.findAll().stream()
                    .filter(po -> po.getProviderOrderId().equals(intentionId))
                    .findFirst()
                    .flatMap(po -> transactionRepo.findFirstByProviderOrderAndStatusIn(
                            po, List.of(PaymentStatus.PENDING, PaymentStatus.PROCESSING)))
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private void applyWebhookToTransaction(PaymentTransaction tx, JsonNode payload, String providerTxnId) {
        JsonNode obj = payload.has("transaction") ? payload.get("transaction") : payload;

        tx.setProviderTransactionId(providerTxnId);
        tx.setStatus(PaymentStatus.PROCESSING);

        boolean success = obj.has("success") && obj.get("success").asBoolean();
        boolean pending = obj.has("pending") && obj.get("pending").asBoolean();

        if (success) {
            tx.setStatus(PaymentStatus.SUCCESS);
        } else if (!pending) {
            tx.setStatus(PaymentStatus.FAILED);
            if (obj.has("data") && obj.get("data").has("message")) {
                tx.setFailureReason(obj.get("data").get("message").asText());
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
