package com.buyology.ecommerce.refund.service;

import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import com.buyology.ecommerce.common.service.EmailService;
import com.buyology.ecommerce.common.utils.SecurityUtils;
import com.buyology.ecommerce.currency.service.CurrencyExchangeService;
import com.buyology.ecommerce.infrastructure.external.ContaboObjectService;
import com.buyology.ecommerce.order.domain.Order;
import com.buyology.ecommerce.order.domain.enums.OrderStatus;
import com.buyology.ecommerce.order.exception.OrderNotFoundException;
import com.buyology.ecommerce.order.repository.OrderRepository;
import com.buyology.ecommerce.payment.config.PaymobProperties;
import com.buyology.ecommerce.payment.domain.PaymentRefund;
import com.buyology.ecommerce.payment.domain.PaymentTransaction;
import com.buyology.ecommerce.payment.dto.CourierFeeChargeRequest;
import com.buyology.ecommerce.payment.dto.PaymentInitiatedResponse;
import com.buyology.ecommerce.payment.enums.PaymentStatus;
import com.buyology.ecommerce.payment.enums.RefundStatus;
import com.buyology.ecommerce.payment.event.CourierFeePaidEvent;
import com.buyology.ecommerce.payment.repository.PaymentRefundRepository;
import com.buyology.ecommerce.payment.repository.PaymentTransactionRepository;
import com.buyology.ecommerce.payment.service.PaymentService;
import com.buyology.ecommerce.payment.service.PaymobClient;
import com.buyology.ecommerce.refund.domain.RefundRequest;
import com.buyology.ecommerce.refund.domain.RefundSetting;
import com.buyology.ecommerce.refund.dto.RefundRequestResponse;
import com.buyology.ecommerce.refund.dto.SetReturnMethodRequest;
import com.buyology.ecommerce.refund.dto.SetReturnMethodResponse;
import com.buyology.ecommerce.refund.enums.RefundRequestStatus;
import com.buyology.ecommerce.refund.enums.RefundReturnMethod;
import com.buyology.ecommerce.refund.exception.RefundException;
import com.buyology.ecommerce.refund.repository.RefundRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class RefundRequestService {

    private static final Logger log = LoggerFactory.getLogger(RefundRequestService.class);

    private static final int MIN_IMAGES = 3;
    private static final int MAX_IMAGES = 8;
    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024; // 5 MB

    private final RefundRequestRepository repository;
    private final RefundSettingService settingService;
    private final OrderRepository orderRepository;
    private final PaymentTransactionRepository paymentTxRepository;
    private final PaymentRefundRepository paymentRefundRepository;
    private final AuthCredentialRepository authCredentialRepository;
    private final ContaboObjectService objectService;
    private final CurrencyExchangeService currencyService;
    private final EmailService emailService;
    private final PaymobClient paymobClient;
    private final PaymobProperties paymobProperties;
    private final PaymentService paymentService;

    private final com.buyology.ecommerce.notification.service.PushNotificationService pushService;
    private final com.buyology.ecommerce.role.repository.UserRoleRepository userRoleRepository;

    public RefundRequestService(RefundRequestRepository repository,
                                RefundSettingService settingService,
                                OrderRepository orderRepository,
                                PaymentTransactionRepository paymentTxRepository,
                                PaymentRefundRepository paymentRefundRepository,
                                AuthCredentialRepository authCredentialRepository,
                                ContaboObjectService objectService,
                                CurrencyExchangeService currencyService,
                                EmailService emailService,
                                PaymobClient paymobClient,
                                PaymobProperties paymobProperties,
                                PaymentService paymentService,
                                com.buyology.ecommerce.notification.service.PushNotificationService pushService,
                                com.buyology.ecommerce.role.repository.UserRoleRepository userRoleRepository) {
        this.pushService = pushService;
        this.userRoleRepository = userRoleRepository;
        this.repository = repository;
        this.settingService = settingService;
        this.orderRepository = orderRepository;
        this.paymentTxRepository = paymentTxRepository;
        this.paymentRefundRepository = paymentRefundRepository;
        this.authCredentialRepository = authCredentialRepository;
        this.objectService = objectService;
        this.currencyService = currencyService;
        this.emailService = emailService;
        this.paymobClient = paymobClient;
        this.paymobProperties = paymobProperties;
        this.paymentService = paymentService;
    }

    // ── Customer ─────────────────────────────────────────────────────────────

    @Transactional
    public RefundRequestResponse createRequest(UUID userId, UUID orderId, String description,
                                               List<MultipartFile> images) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Description is required");
        }
        if (images == null || images.size() < MIN_IMAGES) {
            throw new IllegalArgumentException("At least " + MIN_IMAGES + " images are required");
        }
        if (images.size() > MAX_IMAGES) {
            throw new IllegalArgumentException("No more than " + MAX_IMAGES + " images are allowed");
        }
        for (MultipartFile f : images) {
            if (f.isEmpty()) {
                throw new IllegalArgumentException("Empty image upload");
            }
            if (f.getSize() > MAX_IMAGE_BYTES) {
                throw new IllegalArgumentException("Each image must be ≤ 5 MB");
            }
            String type = f.getContentType();
            if (type == null || !type.startsWith("image/")) {
                throw new IllegalArgumentException("Only image uploads are allowed");
            }
        }

        RefundSetting setting = settingService.getOrCreate();
        if (!setting.isEnabled()) {
            throw new RefundException("Refunds are currently disabled");
        }

        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new RefundException("Only delivered orders are eligible for a refund");
        }
        if (order.getDeliveredAt() == null) {
            throw new RefundException("Order is missing a delivery timestamp");
        }
        Instant deadline = order.getDeliveredAt().plus(setting.getRefundWindowDays(), ChronoUnit.DAYS);
        if (Instant.now().isAfter(deadline)) {
            throw new RefundException("Refund window has expired (" + setting.getRefundWindowDays() + " days)");
        }
        if (order.getPaymentTransactionId() == null) {
            throw new RefundException("Order has no associated payment transaction");
        }

        boolean hasActive = repository.findAllByOrderId(orderId).stream()
                .anyMatch(r -> r.getStatus() != RefundRequestStatus.REJECTED
                        && r.getStatus() != RefundRequestStatus.FAILED);
        if (hasActive) {
            throw new RefundException("An active refund request already exists for this order");
        }

        RefundRequest req = new RefundRequest();
        req.setOrderId(orderId);
        req.setUserId(userId);
        req.setPaymentTransactionId(order.getPaymentTransactionId());
        req.setDescription(description.trim());
        req.setStatus(RefundRequestStatus.PENDING_REVIEW);
        req.setRefundAmount(order.getTotalAmount());
        req.setRefundCurrency(order.getCurrency());
        // Persist first so we have an id to use as the S3 prefix.
        req = repository.save(req);

        List<String> keys = new ArrayList<>();
        for (MultipartFile f : images) {
            String safeName = sanitizeFilename(f.getOriginalFilename());
            String key = "refunds/" + req.getId() + "/" + UUID.randomUUID() + "_" + safeName;
            keys.add(objectService.uploadFile(key, f));
        }
        req.setImageKeys(keys);
        req = repository.save(req);

        notifyRequestReceived(req, order);
        // The dashboard bell must not miss this — every superadmin gets a feed row.
        try {
            java.util.Map<String, String> data = java.util.Map.of("id", req.getId().toString(), "type", "REFUND_REQUEST");
            String body = "Refund requested for order BUY-" + req.getOrderId().toString().substring(0, 8).toUpperCase() + ".";
            userRoleRepository.findUserIdsByRoleName("SUPERADMIN").forEach(uid ->
                    pushService.sendToUser(uid, "New refund request", body, "REFUND_REQUEST", data));
        } catch (Exception e) {
            log.warn("[NOTIFY] superadmin fan-out failed: {}", e.getMessage());
        }

        return toResponse(req);
    }

    @Transactional(readOnly = true)
    public Page<RefundRequestResponse> listForCustomer(UUID userId, int page, int size) {
        return repository.findAllByUserId(userId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public RefundRequestResponse getForCustomer(UUID id, UUID userId) {
        RefundRequest req = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new RefundException("Refund request not found"));
        return toResponse(req);
    }

    // ── Supplier (read-only) ──────────────────────────────────────────────────

    /** Refunds that involve the given supplier's products. Read-only visibility. */
    @Transactional(readOnly = true)
    public Page<RefundRequestResponse> listForSupplier(UUID supplierId, RefundRequestStatus status,
                                                       int page, int size) {
        return repository.findAllForSupplier(supplierId, status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public RefundRequestResponse getForSupplier(UUID id, UUID supplierId) {
        RefundRequest req = repository.findByIdForSupplier(id, supplierId)
                .orElseThrow(() -> new RefundException("Refund request not found"));
        return toResponse(req);
    }

    @Transactional
    public SetReturnMethodResponse setReturnMethod(UUID id, UUID userId, SetReturnMethodRequest request) {
        RefundRequest req = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new RefundException("Refund request not found"));

        // Customer-initiated, money-relevant action (the courier fee is charged
        // separately via Paymob): verify the caller actually owns the underlying order.
        // The order carries an authCredentialId; resolve it to a Users.id and assert
        // the authenticated principal is that user (403 otherwise).
        assertOwnsOrder(req.getOrderId());

        // APPROVED is the first choice; COURIER_FEE_PENDING lets the customer retry the
        // courier-fee payment (or switch to drop-off) if they abandoned the first attempt.
        if (req.getStatus() != RefundRequestStatus.APPROVED
                && req.getStatus() != RefundRequestStatus.COURIER_FEE_PENDING) {
            throw new RefundException("Return method can only be set on an approved request");
        }

        Order order = orderRepository.findById(req.getOrderId()).orElse(null);

        if (request.method() == RefundReturnMethod.COURIER_PICKUP) {
            RefundSetting setting = settingService.getOrCreate();
            String currency = (request.currency() == null || request.currency().isBlank())
                    ? setting.getCourierFeeCurrency()
                    : request.currency().toUpperCase();
            BigDecimal feeLocal = currencyService.convert(
                    setting.getCourierFeeAed(), setting.getCourierFeeCurrency(), currency);
            req.setCourierFeeAmount(feeLocal);
            req.setCourierFeeCurrency(currency);
            req.setReturnMethod(RefundReturnMethod.COURIER_PICKUP);
            // Hard gate: the request stays in COURIER_FEE_PENDING until the customer pays
            // the courier fee through Paymob. The CourierFeePaidEvent webhook advances it
            // to COURIER_REQUESTED. The fee is collected on top of the order and is NOT
            // deducted from the refund (see pay()).
            req.setStatus(RefundRequestStatus.COURIER_FEE_PENDING);
            req = repository.save(req);

            PaymentInitiatedResponse payment = initiateCourierFee(req, order, currency, feeLocal, request.redirectionUrl());
            return new SetReturnMethodResponse(toResponse(req), payment);
        }

        req.setCourierFeeAmount(null);
        req.setCourierFeeCurrency(null);
        req.setReturnMethod(RefundReturnMethod.STORE_DROPOFF);
        req.setStatus(RefundRequestStatus.DROPOFF_SELECTED);
        req = repository.save(req);
        notifyMethodConfirmed(req, order);
        return new SetReturnMethodResponse(toResponse(req), null);
    }

    /** Build and fire the standalone Paymob charge for the courier pickup fee. */
    private PaymentInitiatedResponse initiateCourierFee(RefundRequest req, Order order,
                                                        String currency, BigDecimal feeLocal,
                                                        String redirectionUrl) {
        if (order == null || order.getAuthCredentialId() == null) {
            throw new RefundException("Order is not linked to an account; cannot charge the courier fee");
        }
        String billingName = customerNameFor(order);
        CourierFeeChargeRequest charge = new CourierFeeChargeRequest(
                req.getId(),
                null, // repairId — this is a refund fee
                null, // sellRequestId — this is a refund fee
                order.getAuthCredentialId(),
                feeLocal,
                currency,
                emailFor(order),
                order.getRecipientPhone(),
                "there".equals(billingName) ? null : billingName,
                redirectionUrl);
        return paymentService.initiateCourierFeePayment(charge);
    }

    /**
     * Advances a refund request from COURIER_FEE_PENDING to COURIER_REQUESTED once its
     * standalone courier-fee charge succeeds. Runs in a fresh transaction after the
     * payment transaction commits to SUCCESS (mirrors the order paid-listener), so a
     * failure here never rolls back the payment.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCourierFeePaid(CourierFeePaidEvent event) {
        if (event.refundRequestId() == null) return;
        RefundRequest req = repository.findById(event.refundRequestId()).orElse(null);
        if (req == null) {
            log.warn("[REFUND] CourierFeePaidEvent for unknown refund {}", event.refundRequestId());
            return;
        }
        if (req.getStatus() != RefundRequestStatus.COURIER_FEE_PENDING) {
            log.info("[REFUND] CourierFeePaidEvent for refund {} ignored (status {})", req.getId(), req.getStatus());
            return;
        }
        req.setStatus(RefundRequestStatus.COURIER_REQUESTED);
        req = repository.save(req);
        log.info("[REFUND] Courier fee paid — refund {} advanced to COURIER_REQUESTED", req.getId());
        Order order = orderRepository.findById(req.getOrderId()).orElse(null);
        notifyMethodConfirmed(req, order);
    }

    /**
     * Ownership guard for customer-initiated refund actions. The client supplies
     * the order indirectly; the order stores an {@code authCredentialId} (NOT a
     * Users.id), so we resolve the credential to its owning Users.id and assert
     * the authenticated principal matches it. Throws 403 on mismatch.
     */
    private void assertOwnsOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        UUID authCredentialId = order.getAuthCredentialId();
        if (authCredentialId == null) {
            throw new RefundException("Order is not linked to an account");
        }
        UUID ownerUserId = authCredentialRepository.findById(authCredentialId)
                .map(c -> c.getUserId())
                .orElseThrow(() -> new RefundException("Order owner could not be resolved"));
        SecurityUtils.requireSelf(ownerUserId);
    }

    // ── Admin ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<RefundRequestResponse> listForAdmin(RefundRequestStatus status, UUID storeId, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<RefundRequest> result;
        if (storeId != null) {
            // Scope to refunds whose order contains an item priced from that store.
            result = repository.findAllForStore(storeId, status, pageable);
        } else if (status == null) {
            result = repository.findAll(pageable);
        } else {
            result = repository.findAllByStatus(status, pageable);
        }
        return result.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public RefundRequestResponse getForAdmin(UUID id) {
        return toResponse(loadOrThrow(id));
    }

    @Transactional
    public RefundRequestResponse approve(UUID id, UUID adminId, String adminNote) {
        RefundRequest req = loadOrThrow(id);
        if (req.getStatus() != RefundRequestStatus.PENDING_REVIEW) {
            throw new RefundException("Only pending-review requests can be approved");
        }
        req.setStatus(RefundRequestStatus.APPROVED);
        req.setApprovedAt(Instant.now());
        req.setReviewedBy(adminId);
        req.setAdminNote(adminNote);
        req = repository.save(req);
        Order order = orderRepository.findById(req.getOrderId()).orElse(null);
        notifyApproved(req, order);
        return toResponse(req);
    }

    @Transactional
    public RefundRequestResponse reject(UUID id, UUID adminId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Rejection reason is required");
        }
        RefundRequest req = loadOrThrow(id);
        if (req.getStatus() != RefundRequestStatus.PENDING_REVIEW) {
            throw new RefundException("Only pending-review requests can be rejected");
        }
        req.setStatus(RefundRequestStatus.REJECTED);
        req.setReviewedBy(adminId);
        req.setRejectionReason(reason);
        req = repository.save(req);
        Order order = orderRepository.findById(req.getOrderId()).orElse(null);
        notifyRejected(req, order);
        return toResponse(req);
    }

    @Transactional
    public RefundRequestResponse markReceived(UUID id, UUID adminId) {
        RefundRequest req = loadOrThrow(id);
        if (req.getStatus() != RefundRequestStatus.DROPOFF_SELECTED
                && req.getStatus() != RefundRequestStatus.COURIER_REQUESTED) {
            throw new RefundException("Request must have a return method selected before being marked received");
        }
        req.setStatus(RefundRequestStatus.RECEIVED);
        req.setReceivedAt(Instant.now());
        req.setReviewedBy(adminId);
        req = repository.save(req);
        Order order = orderRepository.findById(req.getOrderId()).orElse(null);
        notifyProductReceived(req, order);
        return toResponse(req);
    }

    @Transactional
    public RefundRequestResponse pay(UUID id, UUID adminId) {
        RefundRequest req = loadOrThrow(id);
        if (req.getStatus() == RefundRequestStatus.PAID) {
            return toResponse(req); // idempotent
        }
        if (req.getStatus() != RefundRequestStatus.RECEIVED
                && req.getStatus() != RefundRequestStatus.FAILED) {
            throw new RefundException("Refund can only be paid after the product is received");
        }

        // Lock the transaction row so two concurrent pay()/refund attempts
        // serialize on the read-check-write of refund state instead of racing.
        PaymentTransaction txn = paymentTxRepository.findWithLockById(req.getPaymentTransactionId())
                .orElseThrow(() -> new RefundException("Payment transaction not found"));
        if (txn.getPaymobTransactionId() == null) {
            throw new RefundException("Payment transaction has no Paymob transaction id — cannot refund");
        }

        // Refundable = full charged amount − amount already refunded. The courier
        // pickup fee is NOT deducted here: the customer pays it separately via Paymob
        // when requesting the courier (purpose = COURIER_RETURN_FEE), so the order is
        // refunded in full. All math is done in the transaction's currency (what Paymob charged).
        String txCurrency = txn.getCurrency();

        // SUCCESS *and* PENDING. A PENDING row is a refund whose outcome never came back — a
        // timeout, a crash, a rolled-back transaction — which is money that may already have left.
        // Counting only SUCCESS treats every one of those as "nothing happened" and lets this pay
        // it again.
        BigDecimal alreadyRefunded = paymentRefundRepository.sumRefundedOrInFlight(txn);
        if (alreadyRefunded == null) {
            alreadyRefunded = BigDecimal.ZERO;
        }

        BigDecimal refundAmount = txn.getAmount()
                .subtract(alreadyRefunded)
                .setScale(2, java.math.RoundingMode.HALF_UP);
        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RefundException(
                    "Nothing to refund: charged " + txn.getAmount() + " " + txCurrency
                            + ", already refunded " + alreadyRefunded);
        }

        long amountCents = refundAmount
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .longValueExact();

        PaymentRefund refundRow = new PaymentRefund();
        refundRow.setTransaction(txn);
        refundRow.setAmount(refundAmount);
        refundRow.setAmountCents(amountCents);
        refundRow.setCurrency(txCurrency);
        refundRow.setReason("Refund request " + req.getId());
        refundRow.setRefundedBy(adminId);
        refundRow.setStatus(RefundStatus.PENDING);
        refundRow = paymentRefundRepository.save(refundRow);

        Order order = orderRepository.findById(req.getOrderId()).orElse(null);

        try {
            String providerRefundId = paymobClient.refund(
                    paymobProperties.getSecretKey(),
                    paymobProperties.getBaseUrl(),
                    String.valueOf(txn.getPaymobTransactionId()),
                    amountCents);
            refundRow.setProviderRefundId(providerRefundId);
            refundRow.setStatus(RefundStatus.SUCCESS);
            paymentRefundRepository.save(refundRow);

            // Fully refunded only when total successful refunds reach the charged amount;
            // otherwise (e.g. courier fee withheld) the transaction is partially refunded.
            BigDecimal totalRefunded = alreadyRefunded.add(refundAmount);
            txn.setStatus(totalRefunded.compareTo(txn.getAmount()) >= 0
                    ? PaymentStatus.REFUNDED
                    : PaymentStatus.PARTIALLY_REFUNDED);
            paymentTxRepository.save(txn);

            req.setPaymentRefundId(refundRow.getId());
            req.setStatus(RefundRequestStatus.PAID);
            req.setPaidAt(Instant.now());
            req.setPaidBy(adminId);
            req = repository.save(req);

            notifyCompleted(req, order);
            return toResponse(req);
        } catch (Exception e) {
            log.error("Paymob refund failed for request {}: {}", req.getId(), e.getMessage(), e);
            refundRow.setStatus(RefundStatus.FAILED);
            refundRow.setNotes(truncate(e.getMessage(), 1000));
            paymentRefundRepository.save(refundRow);

            req.setStatus(RefundRequestStatus.FAILED);
            req = repository.save(req);

            notifyFailed(req, order);
            throw new RefundException("Refund failed: " + e.getMessage());
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private RefundRequest loadOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new RefundException("Refund request not found"));
    }

    private RefundRequestResponse toResponse(RefundRequest r) {
        List<String> urls = r.getImageKeys() == null
                ? List.of()
                : r.getImageKeys().stream().map(objectService::getPresignedUrl).toList();
        return new RefundRequestResponse(
                r.getId(), r.getOrderId(), r.getUserId(), r.getDescription(), urls,
                r.getStatus(), r.getReturnMethod(),
                r.getCourierFeeAmount(), r.getCourierFeeCurrency(),
                r.getRefundAmount(), r.getRefundCurrency(),
                r.getAdminNote(), r.getRejectionReason(),
                r.getCreatedAt(), r.getApprovedAt(), r.getReceivedAt(), r.getPaidAt()
        );
    }

    private String sanitizeFilename(String original) {
        if (original == null || original.isBlank()) return "image";
        String cleaned = original.replaceAll("[^a-zA-Z0-9._-]", "_");
        return cleaned.length() > 80 ? cleaned.substring(cleaned.length() - 80) : cleaned;
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    // ── Email dispatch ──────────────────────────────────────────────────────

    private String emailFor(Order order) {
        if (order == null || order.getAuthCredentialId() == null) return null;
        return authCredentialRepository.findById(order.getAuthCredentialId())
                .map(c -> c.getEmail())
                .orElse(null);
    }

    private String customerNameFor(Order order) {
        if (order == null) return "there";
        String first = order.getRecipientFirstName();
        String last = order.getRecipientLastName();
        if (first == null && last == null) return "there";
        if (first == null) return last;
        if (last == null) return first;
        return first + " " + last;
    }

    private String orderNumberFor(Order order) {
        return order == null || order.getId() == null ? "" : order.getId().toString();
    }

    private void notifyRequestReceived(RefundRequest req, Order order) {
        String to = emailFor(order);
        if (to == null) return;
        emailService.sendRefundRequestReceivedEmail(
                to, customerNameFor(order), orderNumberFor(order), req.getId().toString());
    }

    private void notifyApproved(RefundRequest req, Order order) {
        String to = emailFor(order);
        if (to == null) return;
        RefundSetting setting = settingService.getOrCreate();
        String fee = setting.getCourierFeeAed().toPlainString() + " " + setting.getCourierFeeCurrency();
        emailService.sendRefundApprovedEmail(
                to, customerNameFor(order), orderNumberFor(order), req.getId().toString(), fee);
    }

    private void notifyRejected(RefundRequest req, Order order) {
        String to = emailFor(order);
        if (to == null) return;
        emailService.sendRefundRejectedEmail(
                to, customerNameFor(order), orderNumberFor(order),
                req.getId().toString(), req.getRejectionReason());
    }

    private void notifyMethodConfirmed(RefundRequest req, Order order) {
        String to = emailFor(order);
        if (to == null) return;
        String method;
        String instructions;
        if (req.getReturnMethod() == RefundReturnMethod.COURIER_PICKUP) {
            method = "Courier pickup";
            instructions = "We've received your pickup fee of "
                    + req.getCourierFeeAmount().toPlainString() + " " + req.getCourierFeeCurrency()
                    + ". A courier will be in touch to schedule pickup. This fee is charged separately and "
                    + "does not reduce your refund — your order will be refunded in full.";
        } else {
            method = "Drop off at our store";
            instructions = "Please bring the product to our store at your convenience along with your refund request ID.";
        }
        emailService.sendRefundMethodConfirmedEmail(
                to, customerNameFor(order), orderNumberFor(order),
                req.getId().toString(), method, instructions);
    }

    private void notifyProductReceived(RefundRequest req, Order order) {
        String to = emailFor(order);
        if (to == null) return;
        emailService.sendRefundProductReceivedEmail(
                to, customerNameFor(order), orderNumberFor(order), req.getId().toString());
    }

    private void notifyCompleted(RefundRequest req, Order order) {
        String to = emailFor(order);
        if (to == null) return;
        emailService.sendRefundCompletedEmail(
                to, customerNameFor(order), orderNumberFor(order),
                req.getId().toString(),
                req.getRefundAmount().toPlainString(), req.getRefundCurrency());
    }

    private void notifyFailed(RefundRequest req, Order order) {
        String to = emailFor(order);
        if (to == null) return;
        emailService.sendRefundFailedEmail(
                to, customerNameFor(order), orderNumberFor(order), req.getId().toString());
    }
}
