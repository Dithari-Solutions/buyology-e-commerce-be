package com.buyology.ecommerce.payment.service;

import com.buyology.ecommerce.order.event.PaymentSucceededEvent;
import com.buyology.ecommerce.order.event.PaymentFailedEvent;
import com.buyology.ecommerce.payment.domain.*;
import com.buyology.ecommerce.payment.dto.*;
import com.buyology.ecommerce.payment.enums.PaymentMethodType;
import com.buyology.ecommerce.payment.enums.PaymentPurpose;
import com.buyology.ecommerce.order.domain.enums.OrderStatus;
import com.buyology.ecommerce.payment.enums.PaymentStatus;
import com.buyology.ecommerce.payment.enums.RefundStatus;
import com.buyology.ecommerce.payment.event.CourierFeePaidEvent;
import com.buyology.ecommerce.payment.event.RepairCourierFeePaidEvent;
import com.buyology.ecommerce.payment.event.SellCourierFeePaidEvent;
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
    private final RefundClaimStore refundClaimStore;
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
    private final com.buyology.ecommerce.user.service.AccountStatusValidator accountStatusValidator;

    public PaymentService(
            RefundClaimStore refundClaimStore,
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
            com.buyology.ecommerce.auth.repository.AuthCredentialRepository authCredentialRepo,
            com.buyology.ecommerce.user.service.AccountStatusValidator accountStatusValidator) {
        this.providerRepo = providerRepo;
        this.methodConfigRepo = methodConfigRepo;
        this.transactionRepo = transactionRepo;
        this.webhookEventRepo = webhookEventRepo;
        this.processedWebhookEventRepo = processedWebhookEventRepo;
        this.refundRepo = refundRepo;
        this.refundClaimStore = refundClaimStore;
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
        this.accountStatusValidator = accountStatusValidator;
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
        // The authenticated principal IS the users.id (JwtAuthenticationFilter sets it to
        // credentials.getUserId()). checkPaymentReadiness / findUser look up by users.id, so
        // we must pass currentUserId here — NOT req.getCustomerId(), which is an
        // auth_credentials.id and would throw "User not found" (the cause of the opaque 500
        // on mobile, which sends its auth_credentials.id as customerId).
        UUID currentUserId = SecurityUtils.currentUserId();
        // Block payment for accounts pending deletion — they must recover their account first
        // (also prevents charging the cart-first flow before its order is created on success).
        accountStatusValidator.requireActiveAccount(currentUserId);
        // Store-pickup orders need no delivery address; only enforce the address
        // requirement for deliver-to-me orders (the order-first flow — mobile — has
        // the order here, so its delivery method tells us which). The cart-first flow
        // (no appOrderId yet) keeps the address requirement.
        boolean requireAddress = true;
        if (req.getAppOrderId() != null) {
            var preOrder = orderRepoProvider.getObject().findById(req.getAppOrderId()).orElse(null);
            if (preOrder != null
                    && preOrder.getDeliveryMethod() == com.buyology.ecommerce.order.domain.enums.DeliveryMethod.PICKUP) {
                requireAddress = false;
            }
        }
        userProfileService.checkPaymentReadiness(currentUserId, requireAddress);

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
                // An order is payable exactly once. Without this, POSTing /initiate again on an
                // order that is already paid opened a SECOND full-price charge: the customer was
                // debited twice, and the second capture settled a transaction nothing reconciles
                // against an order that was already settled, so it was never refunded either.
                if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
                    throw new IllegalStateException(
                            "Order " + order.getId() + " is " + order.getStatus()
                            + " and cannot be paid again.");
                }

                // PENDING_PAYMENT is not the same as unpaid. An underpayment leaves the order
                // PENDING_PAYMENT with a SUCCESS transaction against it, and a lost success event
                // does the same until reconcileStuckPayments catches up — in both cases money has
                // already been captured for this order, and charging again would take the FULL
                // amount a second time. REFUNDED is deliberately absent from the list: money given
                // back leaves the order legitimately payable.
                transactionRepo.findFirstByAppOrderIdAndStatusIn(order.getId(),
                                List.of(PaymentStatus.SUCCESS, PaymentStatus.PARTIALLY_REFUNDED))
                        .ifPresent(settled -> {
                            throw new IllegalStateException("Order " + order.getId()
                                    + " already has a settled payment (" + settled.getId()
                                    + "). It cannot be charged again — contact support.");
                        });

                BigDecimal orderTotal = order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount();
                BigDecimal totalInReqCcy = order.getCurrency() != null
                        && order.getCurrency().equalsIgnoreCase(req.getCurrency())
                        ? orderTotal
                        : currencyExchangeService.convert(orderTotal, order.getCurrency(), req.getCurrency());

                // The order's total is what gets charged — not the client's number.
                //
                // This used to take req.getAmount() and merely check it against a 1% tolerance,
                // "for rounding / FX drift". Rounding error is ABSOLUTE — a fraction of a currency
                // unit — so a proportional band is the wrong shape for it, and FX drift does not
                // exist at all when the client pays in the order's own currency. What the band
                // actually bought was a discount: any shopper could pay 99% of any order and the
                // order completed normally, so the loss scaled with the basket — five dirhams on a
                // 500 AED order, a hundred and fifty on a 15,000 AED one, repeatable.
                //
                // Nothing about the client's figure is worth keeping, so it is replaced rather than
                // validated. A mismatch is still logged, because a client that computes a different
                // total is usually a storefront bug worth seeing (it is how the express delivery fee
                // discrepancy would have surfaced).
                if (req.getAmount() != null
                        && req.getAmount().subtract(totalInReqCcy).abs().compareTo(new BigDecimal("0.01")) > 0) {
                    log.warn("[PAYMENT] Order {}: client asked to pay {} {} but the order total is {} {}. "
                                    + "Charging the order total.",
                            order.getId(), req.getAmount(), req.getCurrency(), totalInReqCcy, req.getCurrency());
                }
                effectiveAmount = totalInReqCcy;
            }
            if (order != null && order.getCreditApplied() != null
                    && order.getCreditApplied().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal creditInRequestCcy = order.getCreditCurrency() != null
                        && order.getCreditCurrency().equalsIgnoreCase(req.getCurrency())
                        ? order.getCreditApplied()
                        : currencyExchangeService.convert(order.getCreditApplied(),
                                order.getCreditCurrency(), req.getCurrency());
                effectiveAmount = effectiveAmount
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
        // The ORDER is the last-resort source for billing data, and in practice the usual one:
        // storefront clients send appOrderId without addressId, and Paymob's hosted page then
        // showed "NA" for street/city/apartment on every checkout even though the order carried
        // the customer's full address all along. Same entity in the same transaction, so this is
        // an identity-map hit rather than a second query.
        var billingOrder = req.getAppOrderId() != null
                ? orderRepoProvider.getObject().findById(req.getAppOrderId()).orElse(null)
                : null;
        ObjectNode billingData = buildBillingData(req, address, billingOrder);

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

        // 2. Call Paymob API using tx.id as merchant_order_id. Any failure here (HTTP error,
        // malformed response, parse NPE, …) is converted to a PaymentGatewayException so the
        // client gets a clear 502 message instead of the opaque "An unexpected error occurred".
        PaymobClient.IntentionResult intention;
        try {
            intention = paymobClient.createIntention(
                    provider.getSecretKey(), provider.getBaseUrl(),
                    amountCents, targetCurrency,
                    integrationId, tx.getId().toString(),
                    billingData, customer, items,
                    provider.getNotificationUrl(),
                    redirectionUrl);
        } catch (com.buyology.ecommerce.payment.exception.PaymentGatewayException e) {
            throw e;
        } catch (Exception e) {
            log.error("[PAYMENT] Paymob intention creation failed", e);
            throw new com.buyology.ecommerce.payment.exception.PaymentGatewayException(
                    "Payment gateway error: "
                            + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()),
                    e);
        }

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
     * Initiate a STANDALONE Paymob charge for a courier fee — a refund return pickup, a repair
     * device pickup/return, or a sell-request device pickup/return.
     * Unlike {@link #initiatePayment}, this is not tied to a cart or order: the transaction is
     * tagged with the matching {@link PaymentPurpose} and linked to the owning refund / repair /
     * sell request, so the webhook routes its success back to that module (advancing a refund to
     * COURIER_REQUESTED, a repair or sell request to AWAITING_DEVICE, …). Refund fees are
     * additionally reported as delivery-fee revenue.
     * Card only — instalment providers don't make sense for a small fee.
     */
    @Transactional
    public PaymentInitiatedResponse initiateCourierFeePayment(CourierFeeChargeRequest req) {
        // req.customerId() is an auth_credentials.id, but checkPaymentReadiness keys by the
        // users.id principal (userRepo.findById + requireSelfOrAdmin) — passing the credential
        // id makes findUser throw (→ 500). Resolve the owning user first, then check readiness.
        UUID ownerUserId = authCredentialRepo.findById(req.customerId())
                .map(c -> c.getUserId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown payer for courier fee: " + req.customerId()));
        userProfileService.checkPaymentReadiness(ownerUserId);

        // Exactly one of repairId / sellRequestId / refundRequestId is set — that picks the
        // purpose, the resume lookup and the paid-event the webhook publishes.
        boolean isRepair = req.repairId() != null;
        boolean isSell = req.sellRequestId() != null;
        PaymentPurpose purpose = isRepair ? PaymentPurpose.REPAIR_COURIER_FEE
                : isSell ? PaymentPurpose.SELL_COURIER_FEE
                : PaymentPurpose.COURIER_RETURN_FEE;

        // EXACTLY ONE courier-fee charge per refund/repair/sell request. Re-selecting courier must
        // reuse the existing non-terminal charge, never create a second transaction/intention — a
        // duplicate collides on a unique payment_transactions column → 409. So we resume the same
        // charge.
        List<PaymentStatus> resumable = List.of(PaymentStatus.PENDING, PaymentStatus.PROCESSING);
        PaymentTransaction existing = isRepair
                ? transactionRepo.findFirstByRepairIdAndPurposeAndStatusInOrderByCreatedAtDesc(
                        req.repairId(), purpose, resumable).orElse(null)
                : isSell
                ? transactionRepo.findFirstBySellRequestIdAndPurposeAndStatusInOrderByCreatedAtDesc(
                        req.sellRequestId(), purpose, resumable).orElse(null)
                : (req.refundRequestId() == null ? null
                        : transactionRepo.findFirstByRefundRequestIdAndPurposeAndStatusInOrderByCreatedAtDesc(
                                req.refundRequestId(), purpose, resumable).orElse(null));

        PaymentProvider provider = providerRepo.findFirstByIsActiveTrue()
                .orElseThrow(() -> new IllegalStateException("No active payment provider configured"));
        PaymentMethodConfig config = methodConfigRepo
                .findByProviderAndMethodTypeAndIsActiveTrue(provider, PaymentMethodType.CARD)
                .orElseThrow(() -> new IllegalStateException("No active card config for courier fee payment"));

        // Existing charge already has a live Paymob intention → resume it (return its checkout URL).
        if (existing != null && existing.getPaymentKeyToken() != null) {
            String resumeUrl = provider.getBaseUrl()
                    + "/unifiedcheckout/?publicKey=" + provider.getPublicKey()
                    + "&clientSecret=" + existing.getPaymentKeyToken();
            log.info("[COURIER-FEE] Resuming pending charge {} ({})", existing.getId(),
                    courierFeeSubject(req));
            return buildInitiatedResponse(existing, existing.getPaymentKeyToken(), resumeUrl);
        }

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
        item.put("name", isRepair ? "Repair courier fee"
                : isSell ? "Sell request courier fee"
                : "Courier return pickup fee");
        item.put("amount", amountCents);
        item.put("description", courierFeeSubject(req));
        item.put("quantity", 1);
        items.add(item);

        int integrationId = Integer.parseInt(config.getIntegrationId());

        // Reuse the existing (tokenless) charge if present, else create a fresh one — never a
        // second row for the same refund. createIntention uses the tx id as merchant_order_id.
        PaymentTransaction tx = (existing != null) ? existing
                : savePendingCourierFeeTransaction(req, config, convertedAmount, amountCents, targetCurrency);

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

        log.info("[COURIER-FEE] Created Paymob Intention for {}: tx={}, intention={}",
                courierFeeSubject(req), tx.getId(), intention.intentionId());

        return finalizeTransactionWithProvider(tx.getId(), intention);
    }

    /** "Repair request {id}" / "Sell request {id}" / "Refund request {id}" — for logs and Paymob line items. */
    private static String courierFeeSubject(CourierFeeChargeRequest req) {
        if (req.repairId() != null) return "Repair request " + req.repairId();
        if (req.sellRequestId() != null) return "Sell request " + req.sellRequestId();
        return "Refund request " + req.refundRequestId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentTransaction savePendingCourierFeeTransaction(CourierFeeChargeRequest req,
                                                               PaymentMethodConfig config,
                                                               BigDecimal amount,
                                                               long amountCents,
                                                               String currency) {
        boolean isRepair = req.repairId() != null;
        boolean isSell = req.sellRequestId() != null;
        PaymentTransaction tx = new PaymentTransaction();
        tx.setPurpose(isRepair ? PaymentPurpose.REPAIR_COURIER_FEE
                : isSell ? PaymentPurpose.SELL_COURIER_FEE
                : PaymentPurpose.COURIER_RETURN_FEE);
        if (isRepair) {
            tx.setRepairId(req.repairId());
        } else if (isSell) {
            tx.setSellRequestId(req.sellRequestId());
        } else {
            tx.setRefundRequestId(req.refundRequestId());
        }
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
        log.info("[COURIER-FEE] Committed PENDING courier-fee transaction: id={}, {}", tx.getId(),
                courierFeeSubject(req));
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

        // 1. TRUE IDEMPOTENCY (INSERT-first). Reserve this event in the ledger before doing any
        //    work. A duplicate/replayed delivery collides on the UNIQUE constraint and is skipped
        //    — protecting both the normal and CRED- branches from double-processing (e.g. double
        //    credit on the payback flow).
        //
        //    The key is the transaction id PLUS THE OUTCOME IT REPORTS, and the outcome half is
        //    not optional. Paymob sends one terminal webhook for a card, but an instalment payment
        //    (Tabby, Tamara) reports pending first and success later, on the SAME transaction id.
        //    Keyed on the id alone, that success collided with the earlier pending row and was
        //    discarded as a replay: the money was taken, the transaction stayed PROCESSING and the
        //    order sat in PENDING_PAYMENT forever — never dispatched, never acknowledged. Only
        //    BNPL was affected, which is exactly how it presented.
        //
        //    Including the outcome keeps genuine replays idempotent (the same event carries the
        //    same outcome) while letting a real state change through. The terminal-status check
        //    below is the second guard: a success that arrives twice still only lands once.
        String outcomeKey = webhookOutcome(obj);
        String baseKey = providerTxnIdStr != null ? providerTxnIdStr : maybeMoid;
        String eventKey = baseKey == null ? null : baseKey + ":" + outcomeKey;
        if (baseKey == null) {
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

            boolean isRefundCourierFee = transaction.getPurpose() == PaymentPurpose.COURIER_RETURN_FEE;
            boolean isRepairCourierFee = transaction.getPurpose() == PaymentPurpose.REPAIR_COURIER_FEE;
            boolean isSellCourierFee = transaction.getPurpose() == PaymentPurpose.SELL_COURIER_FEE;
            boolean isCourierFee = isRefundCourierFee || isRepairCourierFee || isSellCourierFee;
            if (transaction.getStatus() == PaymentStatus.SUCCESS) {
                if (isRefundCourierFee) {
                    log.info("[WEBHOOK] Courier return fee paid for refund {}. Publishing CourierFeePaidEvent.",
                            transaction.getRefundRequestId());
                    eventPublisher.publishEvent(new CourierFeePaidEvent(
                            transaction.getRefundRequestId(), transaction.getId(),
                            transaction.getAmount(), transaction.getCurrency()));
                } else if (isRepairCourierFee) {
                    log.info("[WEBHOOK] Repair courier fee paid for repair {}. Publishing RepairCourierFeePaidEvent.",
                            transaction.getRepairId());
                    eventPublisher.publishEvent(new RepairCourierFeePaidEvent(
                            transaction.getRepairId(), transaction.getId(),
                            transaction.getAmount(), transaction.getCurrency()));
                } else if (isSellCourierFee) {
                    log.info("[WEBHOOK] Sell courier fee paid for sell request {}. Publishing SellCourierFeePaidEvent.",
                            transaction.getSellRequestId());
                    eventPublisher.publishEvent(new SellCourierFeePaidEvent(
                            transaction.getSellRequestId(), transaction.getId(),
                            transaction.getAmount(), transaction.getCurrency()));
                } else {
                    log.info("[WEBHOOK] SUCCESS! Publishing PaymentSucceededEvent.");
                    eventPublisher.publishEvent(new PaymentSucceededEvent(transaction.getAppOrderId(), transaction.getId()));
                }
            } else if (transaction.getStatus() == PaymentStatus.FAILED
                    || transaction.getStatus() == PaymentStatus.CANCELLED) {
                if (isCourierFee) {
                    // No order to fail — the refund/repair/sell request simply stays fee-pending so
                    // the customer can retry the fee payment or switch to the free option.
                    log.info("[WEBHOOK] Courier fee charge {} for {}.", transaction.getStatus(),
                            isRepairCourierFee ? "repair " + transaction.getRepairId()
                                    : isSellCourierFee ? "sell request " + transaction.getSellRequestId()
                                    : "refund " + transaction.getRefundRequestId());
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
    /**
     * The outcome a webhook reports, as a short stable token for the idempotency key:
     * {@code success}, {@code pending} or {@code failed}. Mirrors exactly how
     * {@link #applyWebhookToTransaction} reads the same two flags, so the key changes precisely
     * when the resulting status would.
     */
    static String webhookOutcome(JsonNode obj) {
        boolean success = obj.has("success") && obj.get("success").asBoolean();
        if (success) return "success";
        boolean pending = obj.has("pending") && obj.get("pending").asBoolean();
        return pending ? "pending" : "failed";
    }

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

    /**
     * Whether a callback's money matches the transaction it claims to settle.
     *
     * <p>This is the check that makes the signature mean what it appears to mean. Paymob's HMAC
     * covers {@code amount_cents}, {@code currency} and {@code order.id} — but NOT
     * {@code order.merchant_order_id}, which is the field
     * {@link #resolveTransactionFromPayload} uses first to decide WHICH of our transactions the
     * callback belongs to. A signature therefore proves the numbers in the payload are Paymob's;
     * it proves nothing about which of our rows they apply to.
     *
     * <p>Without this comparison, a genuine signed callback for a small payment can be re-pointed
     * at any other transaction by editing that one unsigned field, and the larger transaction is
     * marked SUCCESS on the strength of the smaller one's money. Comparing the amount and currency
     * closes the gap without needing the signature to change: a callback carrying 100 cents can
     * only ever settle a transaction that is owed 100 cents.
     *
     * <p>Absent fields are treated as a mismatch. A callback that does not say what it paid is not
     * evidence that anything was paid.
     */
    private boolean webhookMoneyMatchesTransaction(PaymentTransaction tx, JsonNode obj) {
        if (tx.getAmountCents() == null) {
            log.error("[PAYMENT] Transaction {} has no amountCents; refusing to settle it from a webhook",
                    tx.getId());
            return false;
        }
        if (!obj.hasNonNull("amount_cents")) {
            log.error("[PAYMENT] Webhook for transaction {} carries no amount_cents", tx.getId());
            return false;
        }
        long payloadCents = obj.get("amount_cents").asLong(-1L);
        if (payloadCents != tx.getAmountCents()) {
            log.error("[PAYMENT] REJECTED webhook for transaction {}: payload says {} cents, "
                            + "transaction is {} cents. A signed callback for one payment cannot "
                            + "settle a different one — merchant_order_id is not covered by the HMAC.",
                    tx.getId(), payloadCents, tx.getAmountCents());
            return false;
        }
        String payloadCurrency = obj.hasNonNull("currency") ? obj.get("currency").asText() : null;
        if (payloadCurrency == null || !payloadCurrency.equalsIgnoreCase(tx.getCurrency())) {
            log.error("[PAYMENT] REJECTED webhook for transaction {}: payload currency {} != {}",
                    tx.getId(), payloadCurrency, tx.getCurrency());
            return false;
        }
        return true;
    }

    /**
     * Remember which card paid, for the customer's order page. Paymob's {@code source_data.pan}
     * arrives already masked (e.g. {@code 512345xxxxxx1234}); only its last four digits and the
     * brand ({@code sub_type}) are stored — never anything resembling a full PAN. Failure
     * callbacks carry it too, which is fine: the display joins through a SUCCESS transaction.
     */
    private void captureCardIdentity(PaymentTransaction tx, JsonNode obj) {
        JsonNode src = obj.path("source_data");
        if (src.isMissingNode() || src.isNull()) return;
        String pan = src.path("pan").asText("");
        String digits = pan.replaceAll("\\D", "");
        if (digits.length() >= 4) {
            tx.setCardLast4(digits.substring(digits.length() - 4));
        }
        String brand = src.path("sub_type").asText("");
        if (!brand.isBlank()) {
            tx.setCardBrand(brand.length() > 32 ? brand.substring(0, 32) : brand);
        }
    }

    private void applyWebhookToTransaction(PaymentTransaction tx, JsonNode obj, Long paymobTxnId) {
        tx.setPaymobTransactionId(paymobTxnId);

        boolean success = obj.has("success") && obj.get("success").asBoolean();

        // A successful callback moves money, so it is the one that must prove it belongs here.
        // A failure or pending callback is applied as-is: refusing to record a failure would leave
        // the transaction stuck PENDING, and marking a payment FAILED costs nobody anything.
        if (success && !webhookMoneyMatchesTransaction(tx, obj)) {
            tx.setStatus(PaymentStatus.FAILED);
            tx.setFailureReason("Rejected: callback amount/currency did not match this transaction");
            return;
        }
        // Only after the callback has proven it belongs to THIS transaction — a rejected
        // mismatched callback must not stamp another payment's card onto it.
        captureCardIdentity(tx, obj);
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

    /**
     * Refunds a payment.
     *
     * <p>Deliberately NOT @Transactional. It is orchestration over three steps that must not share
     * one: claim the refund and commit it, call the gateway holding no transaction and no database
     * connection at all, then record the outcome. Wrapping the whole thing again would put an HTTP
     * call back inside a transaction — which is what made a rollback able to erase a refund that
     * had already taken the customer's money.
     */
    public RefundResponse initiateRefund(RefundRequest req) {
        // Locks the transaction, re-checks the refundable balance and writes the PENDING claim, in
        // one transaction that commits before we go anywhere near the gateway. It returns plain
        // values rather than entities because everything below runs with no session open.
        RefundClaimStore.RefundClaim claim = refundClaimStore.lockCheckAndClaim(req);

        String providerRefundId;
        try {
            providerRefundId = paymobClient.refund(
                    claim.secretKey(), claim.baseUrl(), claim.paymobTransactionId(), claim.refundCents());
        } catch (RuntimeException e) {
            // The gateway said no, or said nothing. Only a definite refusal may release the claim;
            // an ambiguous failure has to keep it, because the money may be gone.
            refundClaimStore.releaseOrFailClaim(claim.refundId(), e);
            throw e;
        }

        PaymentRefund refund = refundClaimStore.settleClaim(claim.refundId(), providerRefundId);
        return toRefundResponse(refund, claim.transactionId());
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
            if (req.getPickupStoreId() != null) meta.put("pickupStoreId", req.getPickupStoreId().toString());
            if (req.getShippingFee() != null) meta.put("shippingFee", req.getShippingFee().toPlainString());
            if (req.getDeliveryMethod() != null) meta.put("deliveryMethod", req.getDeliveryMethod());
            return objectMapper.writeValueAsString(meta);
        } catch (Exception e) { return null; }
    }

    /**
     * Billing details for Paymob's hosted page, filled from the best source available.
     *
     * Precedence per field: what the client explicitly sent, then the saved address it named,
     * then <b>the order's own address snapshot</b>, then a non-blank placeholder. Paymob rejects
     * nulls outright ("This field may not be null"), so every field is coalesced — but a
     * placeholder is a last resort, not a default: the order snapshot means a customer who
     * checked out to a real address sees that address on the payment page, which is also what
     * their card issuer checks it against.
     *
     * {@code addressLine2} maps to {@code apartment} because that is what it holds in this
     * product — flat/villa/floor detail typed on the second line of the address form. Paymob's
     * separate floor/building fields have no counterpart in our address model, so they stay
     * placeholders unless a client sends them.
     */
    private ObjectNode buildBillingData(InitiatePaymentRequest req, UserAddress address,
                                        com.buyology.ecommerce.order.domain.Order order) {
        String billingName = req.getBillingName();
        if ((billingName == null || billingName.isBlank()) && order != null) {
            billingName = ((order.getRecipientFirstName() == null ? "" : order.getRecipientFirstName())
                    + " " + (order.getRecipientLastName() == null ? "" : order.getRecipientLastName())).trim();
        }
        String[] nameParts = (billingName != null && !billingName.isBlank())
                ? billingName.split(" ", 2) : new String[]{"NA", "NA"};

        String addrPhone   = address != null ? address.getPhoneNumber() : null;
        String addrStreet  = address != null ? address.getAddressLine1() : null;
        String addrLine2   = address != null ? address.getAddressLine2() : null;
        String addrCity    = address != null ? address.getCity() : null;
        String addrState   = address != null ? address.getState() : null;
        String addrPostal  = address != null ? address.getPostalCode() : null;
        String addrCountry = address != null ? address.getCountry() : null;

        String ordPhone   = order != null ? order.getRecipientPhone() : null;
        String ordStreet  = order != null ? order.getAddressLine1() : null;
        String ordLine2   = order != null ? order.getAddressLine2() : null;
        String ordCity    = order != null ? order.getCity() : null;
        String ordState   = order != null ? order.getState() : null;
        String ordPostal  = order != null ? order.getPostalCode() : null;
        String ordCountry = order != null ? order.getCountry() : null;

        ObjectNode n = objectMapper.createObjectNode();
        n.put("first_name", coalesce(nameParts[0], "NA"));
        n.put("last_name", coalesce(nameParts.length > 1 ? nameParts[1] : null, "NA"));
        n.put("phone_number", coalesce(req.getCustomerPhone(), addrPhone, ordPhone, "NA"));
        n.put("apartment", coalesce(req.getBillingApartment(), addrLine2, ordLine2, "NA"));
        n.put("floor", coalesce(req.getBillingFloor(), "NA"));
        n.put("street", coalesce(req.getBillingStreet(), addrStreet, ordStreet, "NA"));
        n.put("building", coalesce(req.getBillingBuilding(), "NA"));
        n.put("city", coalesce(req.getBillingCity(), addrCity, ordCity, "NA"));
        n.put("country", coalesce(req.getBillingCountry(), addrCountry, ordCountry, "AE"));
        n.put("state", coalesce(req.getBillingState(), addrState, ordState, addrCity, ordCity, "NA"));
        n.put("postal_code", coalesce(req.getBillingPostalCode(), addrPostal, ordPostal, "NA"));
        n.put("email", coalesce(req.getCustomerEmail(), "NA"));
        return n;
    }

    /** First non-null, non-blank value; falls back to "NA" so Paymob never receives null. */
    private static String coalesce(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return "NA";
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
        res.setCardLast4(tx.getCardLast4());
        res.setCardBrand(tx.getCardBrand());
        res.setCreatedAt(tx.getCreatedAt());
        res.setUpdatedAt(tx.getUpdatedAt());
        return res;
    }

    private RefundResponse toRefundResponse(PaymentRefund refund) {
        return toRefundResponse(refund, refund.getTransaction().getId());
    }

    /**
     * Maps a refund whose transaction id is already known.
     *
     * <p>{@code PaymentRefund.transaction} is LAZY, and {@link #initiateRefund} maps its result
     * after every transaction has closed — reading the association there would throw rather than
     * return a response.
     */
    private RefundResponse toRefundResponse(PaymentRefund refund, java.util.UUID transactionId) {
        RefundResponse res = new RefundResponse();
        res.setId(refund.getId());
        res.setTransactionId(transactionId);
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
