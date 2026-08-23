package com.buyology.ecommerce.sell.service;

import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import com.buyology.ecommerce.common.service.EmailService;
import com.buyology.ecommerce.currency.service.CurrencyExchangeService;
import com.buyology.ecommerce.infrastructure.external.ContaboObjectService;
import com.buyology.ecommerce.payment.dto.CourierFeeChargeRequest;
import com.buyology.ecommerce.payment.dto.PaymentInitiatedResponse;
import com.buyology.ecommerce.payment.event.SellCourierFeePaidEvent;
import com.buyology.ecommerce.payment.service.PaymentService;
import com.buyology.ecommerce.sell.domain.DeviceCondition;
import com.buyology.ecommerce.sell.domain.SellDeliveryMethod;
import com.buyology.ecommerce.sell.domain.SellPayoutMethod;
import com.buyology.ecommerce.sell.domain.SellRequest;
import com.buyology.ecommerce.sell.domain.SellStatus;
import com.buyology.ecommerce.sell.dto.SellDeliveryResponse;
import com.buyology.ecommerce.sell.dto.SellRequestResponse;
import com.buyology.ecommerce.sell.dto.SellStoreOptionResponse;
import com.buyology.ecommerce.sell.event.SellRequestSubmittedEvent;
import com.buyology.ecommerce.sell.repository.SellRequestRepository;
import com.buyology.ecommerce.store.domain.StoreLocation;
import com.buyology.ecommerce.store.repository.StoreLocationRepository;
import com.buyology.ecommerce.user.domain.Users;
import com.buyology.ecommerce.user.repository.UserProfilesRepository;
import com.buyology.ecommerce.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Customer sell (trade-in) service — "buy my device".
 *
 * Deliberately the mirror of {@link com.buyology.ecommerce.repair.service.RepairService}: any
 * logged-in customer with complete contact details opens a request (no B2B membership gate),
 * ownership is keyed on the caller's auth_credentials.id (sub) while the JWT filter sets the
 * principal to users.id (uid), the device travels to the store by the same two methods (free store
 * drop-off / 20 AED courier pickup), and the customer is emailed at every stage. What differs is
 * the money direction: procurement quotes what Buyology will PAY, and after the customer accepts
 * they collect that payout at the store.
 *
 * Lifecycle: SUBMITTED → AWAITING_DEVICE → UNDER_REVIEW → OFFER_MADE → ACCEPTED → COMPLETED
 * (plus DECLINED / CANCELLED). The procurement team (SUPERADMIN / PROCUREMENT) drives the
 * admin-side transitions; the dashboard badge is driven by the {@code adminUnread} flag which
 * every customer-side action re-raises.
 */
@Service
public class SellService {

    private static final Logger log = LoggerFactory.getLogger(SellService.class);

    /** Base courier fee (converted to the customer's currency for display/charge). */
    public static final BigDecimal COURIER_FEE_AED = new BigDecimal("20");
    private static final String FEE_BASE_CURRENCY = "AED";
    private static final int MAX_IMAGES = 4;

    private final SellRequestRepository sellRepo;
    private final AuthCredentialRepository authCredentialRepository;
    private final UserRepository userRepository;
    private final UserProfilesRepository userProfilesRepository;
    private final StoreLocationRepository storeLocationRepository;
    private final ContaboObjectService contaboObjectService;
    private final CurrencyExchangeService currencyExchangeService;
    private final EmailService emailService;
    private final PaymentService paymentService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.admin-email:firdovsirz@gmail.com}")
    private String procurementTeamEmail;

    @Value("${app.web-base-url:https://buyology.online}")
    private String webBaseUrl;

    public SellService(SellRequestRepository sellRepo,
                       AuthCredentialRepository authCredentialRepository,
                       UserRepository userRepository,
                       UserProfilesRepository userProfilesRepository,
                       StoreLocationRepository storeLocationRepository,
                       ContaboObjectService contaboObjectService,
                       CurrencyExchangeService currencyExchangeService,
                       EmailService emailService,
                       PaymentService paymentService,
                       ApplicationEventPublisher eventPublisher) {
        this.sellRepo = sellRepo;
        this.authCredentialRepository = authCredentialRepository;
        this.userRepository = userRepository;
        this.userProfilesRepository = userProfilesRepository;
        this.storeLocationRepository = storeLocationRepository;
        this.contaboObjectService = contaboObjectService;
        this.currencyExchangeService = currencyExchangeService;
        this.emailService = emailService;
        this.paymentService = paymentService;
        this.eventPublisher = eventPublisher;
    }

    // =========================================================================
    // Customer
    // =========================================================================

    /**
     * Whether the caller has the contact details a sell request needs (email AND phone). The
     * storefront calls this before rendering the form — a trade-in ends with a human handing over
     * money at a store, so we refuse to take the request at all until we can reach the seller.
     */
    public boolean isProfileComplete(UUID userId) {
        UUID resolvedUserId = resolveUsersId(userId);
        UUID credentialId = resolveCredentialId(userId);
        String email = resolveContactEmail(resolvedUserId, credentialId);
        String phone = resolveContactPhone(resolvedUserId);
        return email != null && !email.isBlank() && phone != null && !phone.isBlank();
    }

    /**
     * Open a new sell request. Product name / brand / model / condition / description are required;
     * purchase date is optional. Up to {@link #MAX_IMAGES} device photos are uploaded to Contabo
     * under {@code sell-requests/{uuid}/{filename}} (only the keys are persisted). Contact
     * email/phone are snapshotted from the caller's profile — and the request is refused outright
     * if either is missing. The customer is emailed a confirmation and procurement is notified —
     * both best-effort.
     */
    @Transactional
    public SellRequestResponse create(UUID userId, String productName, String brand, String model,
                                      LocalDate purchaseDate, DeviceCondition condition,
                                      String description, List<MultipartFile> images) {
        UUID credentialId = resolveCredentialId(userId);
        UUID resolvedUserId = resolveUsersId(userId);

        requireText(productName, "Product name");
        requireText(brand, "Brand");
        requireText(model, "Model");
        requireText(description, "Device description");
        // At least one photo is mandatory: the offer (and the AI valuation) rest on what the photos
        // show. Checked here as well as in the browser because the client-side check is trivially
        // bypassable.
        requireAtLeastOneImage(images);

        String email = resolveContactEmail(resolvedUserId, credentialId);
        String phone = resolveContactPhone(resolvedUserId);
        // Hard gate, not a nudge: we must be able to reach the seller about their device and their
        // money. The storefront hides the form for the same reason, this is the server-side half.
        if (email == null || email.isBlank() || phone == null || phone.isBlank()) {
            throw new IllegalArgumentException(
                    "Please complete your profile (email and phone number) before requesting to sell a device.");
        }

        String imageKeys = uploadImages(images);
        if (imageKeys == null) {
            throw new IllegalArgumentException("At least one photo of the device is required.");
        }
        String name = resolveCustomerName(resolvedUserId);

        SellRequest request = new SellRequest();
        request.setCredentialId(credentialId);
        request.setUserId(resolvedUserId);
        request.setProductName(productName.trim());
        request.setBrand(brand.trim());
        request.setModel(model.trim());
        request.setPurchaseDate(purchaseDate);
        request.setDeviceCondition(condition == null ? DeviceCondition.GOOD : condition);
        request.setDescription(description.trim());
        request.setImageKeys(imageKeys);
        request.setStatus(SellStatus.SUBMITTED);
        request.setContactEmail(email);
        request.setContactPhone(phone);
        request.setAdminUnread(true);
        request.setCustomerUnread(false);
        request.setSubmittedAt(Instant.now());
        request = sellRepo.save(request);
        request.setReference(buildReference());
        request = sellRepo.save(request);

        final SellRequest saved = request;
        best("received email", () -> emailService.sendSellReceivedEmail(
                email, name, saved.getReference(), saved.getProductName()));
        best("team notification", () -> emailService.sendSellTeamNotificationEmail(
                procurementTeamEmail, name, saved.getReference(), saved.getProductName(),
                saved.getBrand(), saved.getModel(), saved.getDeviceCondition().name(),
                saved.getDescription(), webBaseUrl + "/sell/" + saved.getId()));

        // Kicks off the advisory AI buy-back valuation. Consumed AFTER_COMMIT on another thread, so
        // it neither delays this response nor can it fail the submission.
        eventPublisher.publishEvent(new SellRequestSubmittedEvent(saved.getId()));

        return toResponse(request);
    }

    /** A customer's own sell requests, newest first. */
    public List<SellRequestResponse> listOwn(UUID userId) {
        return listOwn(userId, null);
    }

    /**
     * A customer's own sell requests, newest first. When {@code displayCurrency} is supplied, the
     * AED AI valuation is additionally converted into it for display.
     */
    public List<SellRequestResponse> listOwn(UUID userId, String displayCurrency) {
        UUID credentialId = resolveCredentialId(userId);
        return sellRepo.findByCredentialIdOrderByCreatedAtDesc(credentialId).stream()
                .map(r -> toResponse(r, displayCurrency))
                .collect(Collectors.toList());
    }

    /** A single owned sell request. Opening it clears the customer's "new update" flag. */
    @Transactional
    public SellRequestResponse getOwn(UUID userId, UUID id) {
        return getOwn(userId, id, null);
    }

    /**
     * A single owned sell request, with the AI valuation converted into {@code displayCurrency}
     * when given. Opening it clears the customer's "new update" flag.
     */
    @Transactional
    public SellRequestResponse getOwn(UUID userId, UUID id, String displayCurrency) {
        SellRequest request = requireOwner(loadOrThrow(id), userId);
        if (request.isCustomerUnread()) {
            request.setCustomerUnread(false);
            request = sellRepo.save(request);
        }
        return toResponse(request, displayCurrency);
    }

    /**
     * Choose — or change — how the device reaches the store. STORE_DROPOFF requires a store branch
     * and is free, so the request advances to AWAITING_DEVICE immediately. COURIER_PICKUP records
     * the 20 AED fee and returns a Paymob checkout session: the request stays SUBMITTED (courier
     * method recorded, unpaid) until the fee is paid, when the {@link SellCourierFeePaidEvent}
     * webhook advances it to AWAITING_DEVICE.
     *
     * <p>Valid while SUBMITTED <em>or</em> AWAITING_DEVICE — the customer can change their mind
     * right up until the device is with us and the inspection starts (UNDER_REVIEW). A change is
     * recorded on the request ({@code previousInboundDeliveryMethod} +
     * {@code inboundDeliveryChangedAt}) and re-raises the unread flag, so procurement sees it
     * rather than dispatching a courier for a device the customer is now bringing in themselves.
     *
     * <p>Switching away from an already-PAID courier pickup sets {@code courierFeeRefundDue}: we
     * collected money for a collection that will not happen. Nothing refunds automatically — the
     * flag exists so the team can settle it.
     */
    @Transactional
    public SellDeliveryResponse chooseDelivery(UUID userId, UUID id, SellDeliveryMethod method,
                                               UUID storeLocationId, String currency, String redirectionUrl) {
        SellRequest request = requireOwner(loadOrThrow(id), userId);
        if (request.getStatus() != SellStatus.SUBMITTED && request.getStatus() != SellStatus.AWAITING_DEVICE) {
            throw new IllegalStateException(
                    "Your device is already with our team — the delivery method can no longer be changed.");
        }
        if (method != SellDeliveryMethod.COURIER_PICKUP && method != SellDeliveryMethod.STORE_DROPOFF) {
            throw new IllegalArgumentException("Inbound delivery must be COURIER_PICKUP or STORE_DROPOFF.");
        }

        SellDeliveryMethod previous = request.getInboundDeliveryMethod();
        // Re-choosing the courier when its fee is ALREADY PAID must not mint another charge
        // (and silently reset the paid one) — the leg is booked; nothing to do. The unpaid
        // same-method call stays open on purpose: it is the retry path for an abandoned fee.
        if (method == SellDeliveryMethod.COURIER_PICKUP
                && previous == SellDeliveryMethod.COURIER_PICKUP
                && request.isCourierFeePaid()) {
            return new SellDeliveryResponse(toResponse(request), null);
        }
        boolean methodChanged = previous != null && previous != method;
        if (methodChanged) {
            request.setPreviousInboundDeliveryMethod(previous);
            request.setInboundDeliveryChangedAt(Instant.now());
        }

        request.setInboundDeliveryMethod(method);
        if (method == SellDeliveryMethod.STORE_DROPOFF) {
            StoreLocation branch = requireStoreLocation(storeLocationId);
            request.setStoreLocationId(branch.getId());
            if (previous == SellDeliveryMethod.COURIER_PICKUP && request.isCourierFeePaid()) {
                // Keep the amount/currency as the record of what was actually charged.
                request.setCourierFeeRefundDue(true);
            } else {
                request.setCourierFeeAmount(null);
                request.setCourierFeeCurrency(null);
            }
            request.setCourierFeePaid(false);
            request.setStatus(SellStatus.AWAITING_DEVICE);
            request.setAdminUnread(true);
            request = sellRepo.save(request);
            return new SellDeliveryResponse(toResponse(request), null);
        }

        // Courier pickup — the customer pays the fee first; the request drops back to SUBMITTED
        // (unpaid) so a switch from an already-confirmed drop-off doesn't leave it "awaiting" a
        // collection nobody has paid for yet.
        request.setStoreLocationId(null);
        applyCourierFee(request, currency);
        request.setCourierFeePaid(false);
        request.setStatus(SellStatus.SUBMITTED);
        request.setAdminUnread(true);
        request = sellRepo.save(request);
        PaymentInitiatedResponse payment = initiateSellCourierFee(request, redirectionUrl);
        return new SellDeliveryResponse(toResponse(request), payment);
    }

    /**
     * Customer's decision on the buy-back offer (only valid while OFFER_MADE). Accept → ACCEPTED
     * (payout awaiting collection at the store); decline → DECLINED (device must be returned).
     *
     * On accept the customer picks how they take the money. Only {@link SellPayoutMethod#STORE_CASH}
     * is available today — wallet credit is rejected until there is a wallet ledger to credit.
     */
    @Transactional
    public SellRequestResponse respondToOffer(UUID userId, UUID id, boolean accept, SellPayoutMethod payoutMethod) {
        SellRequest request = requireOwner(loadOrThrow(id), userId);
        if (request.getStatus() != SellStatus.OFFER_MADE) {
            throw new IllegalStateException("There is no offer awaiting your response.");
        }
        if (accept) {
            SellPayoutMethod chosen = (payoutMethod == null) ? SellPayoutMethod.STORE_CASH : payoutMethod;
            if (chosen == SellPayoutMethod.WALLET_CREDIT) {
                throw new IllegalArgumentException(
                        "Buyology wallet credit is coming soon — please choose to collect your payment at the store.");
            }
            request.setPayoutMethod(chosen);
        }
        request.setStatus(accept ? SellStatus.ACCEPTED : SellStatus.DECLINED);
        request.setAdminUnread(true);
        request = sellRepo.save(request);

        final SellRequest saved = request;
        best("offer decision email", () -> emailService.sendSellStatusEmail(
                contactEmailFor(saved), resolveCustomerName(saved.getUserId()),
                saved.getReference(), saved.getProductName(),
                stageLabel(saved.getStatus()),
                accept ? "Thanks for accepting — collect your payment at our store and the device is ours."
                       : "You've declined the offer. Please choose how to get your device back."));
        return toResponse(request);
    }

    /**
     * After a decline, choose how the device is returned (only valid while DECLINED). STORE_PICKUP
     * is free and arranges the return immediately. COURIER_RETURN records the 20 AED fee and
     * returns a Paymob checkout session; the return is only arranged once the fee is paid (via the
     * {@link SellCourierFeePaidEvent} webhook).
     */
    @Transactional
    public SellDeliveryResponse chooseReturn(UUID userId, UUID id, SellDeliveryMethod method,
                                              String currency, String redirectionUrl) {
        SellRequest request = requireOwner(loadOrThrow(id), userId);
        if (request.getStatus() != SellStatus.DECLINED) {
            throw new IllegalStateException("A return can only be arranged after declining an offer.");
        }
        if (method != SellDeliveryMethod.COURIER_RETURN && method != SellDeliveryMethod.STORE_PICKUP) {
            throw new IllegalArgumentException("Return delivery must be COURIER_RETURN or STORE_PICKUP.");
        }
        SellDeliveryMethod previousReturn = request.getReturnDeliveryMethod();
        // Re-choosing the courier when its fee is ALREADY PAID must not mint another charge
        // (and silently reset the paid one) — the leg is booked; nothing to do. The unpaid
        // same-method call stays open on purpose: it is the retry path for an abandoned fee.
        if (method == SellDeliveryMethod.COURIER_RETURN
                && previousReturn == SellDeliveryMethod.COURIER_RETURN
                && request.isCourierFeePaid()) {
            return new SellDeliveryResponse(toResponse(request), null);
        }
        request.setReturnDeliveryMethod(method);
        if (method == SellDeliveryMethod.STORE_PICKUP) {
            if (previousReturn == SellDeliveryMethod.COURIER_RETURN && request.isCourierFeePaid()) {
                // The fee was collected for a courier that will now never run — keep the
                // amount on record and flag it so the team settles the refund manually.
                request.setCourierFeeRefundDue(true);
            } else {
                request.setCourierFeeAmount(null);
                request.setCourierFeeCurrency(null);
            }
            request.setCourierFeePaid(false);
            request.setAdminUnread(true);
            request = sellRepo.save(request);
            return new SellDeliveryResponse(toResponse(request), null);
        }

        // Courier return — the customer pays the fee first; the return is arranged when it clears.
        applyCourierFee(request, currency);
        request.setCourierFeePaid(false);
        request = sellRepo.save(request);
        PaymentInitiatedResponse payment = initiateSellCourierFee(request, redirectionUrl);
        return new SellDeliveryResponse(toResponse(request), payment);
    }

    /**
     * Start a Paymob card charge for this request's 20 AED courier fee (settlement in AED). On
     * success the {@link SellCourierFeePaidEvent} webhook calls {@link #onSellCourierFeePaid}.
     */
    private PaymentInitiatedResponse initiateSellCourierFee(SellRequest request, String redirectionUrl) {
        String name = resolveCustomerName(request.getUserId());
        CourierFeeChargeRequest charge = new CourierFeeChargeRequest(
                null,                        // refundRequestId — this is a sell fee
                null,                        // repairId — this is a sell fee
                request.getId(),             // sellRequestId
                request.getCredentialId(),   // payer (auth_credentials.id / sub)
                COURIER_FEE_AED,
                FEE_BASE_CURRENCY,
                contactEmailFor(request),
                request.getContactPhone(),
                (name == null || name.isBlank()) ? null : name,
                redirectionUrl);
        return paymentService.initiateCourierFeePayment(charge);
    }

    /**
     * Advances a sell request once its courier fee clears (Paymob webhook, AFTER_COMMIT). Confirms
     * the inbound pickup (SUBMITTED → AWAITING_DEVICE) or the post-decline return. Idempotent.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onSellCourierFeePaid(SellCourierFeePaidEvent event) {
        SellRequest request = sellRepo.findById(event.sellRequestId()).orElse(null);
        if (request == null) {
            log.warn("[SELL] SellCourierFeePaidEvent for unknown sell request {}", event.sellRequestId());
            return;
        }
        if (request.isCourierFeePaid()) {
            log.info("[SELL] SellCourierFeePaidEvent for request {} ignored (already paid)", request.getId());
            return;
        }
        if (request.getStatus() == SellStatus.SUBMITTED
                && request.getInboundDeliveryMethod() == SellDeliveryMethod.COURIER_PICKUP) {
            request.setCourierFeePaid(true);
            request.setStatus(SellStatus.AWAITING_DEVICE);
            request.setAdminUnread(true);
            sellRepo.save(request);
            emailStatus(request, "Your courier pickup is arranged — we'll collect your device shortly.");
            log.info("[SELL] Courier pickup fee paid — request {} advanced to AWAITING_DEVICE", request.getId());
        } else if (request.getStatus() == SellStatus.DECLINED
                && request.getReturnDeliveryMethod() == SellDeliveryMethod.COURIER_RETURN) {
            request.setCourierFeePaid(true);
            request.setAdminUnread(true);
            sellRepo.save(request);
            emailStatus(request, "Your courier return is arranged — we'll deliver your device back to you.");
            log.info("[SELL] Courier return fee paid — request {} return arranged", request.getId());
        } else {
            log.info("[SELL] SellCourierFeePaidEvent for request {} ignored (status {}, inbound {}, return {})",
                    request.getId(), request.getStatus(), request.getInboundDeliveryMethod(),
                    request.getReturnDeliveryMethod());
        }
    }

    /** Active store branches in a country (alpha-3 code) for the drop-off / pickup picker. */
    public List<SellStoreOptionResponse> listStoreOptions(String country) {
        if (country == null || country.isBlank()) {
            return List.of();
        }
        return storeLocationRepository.findAllByCountryAndIsActive(country.trim().toUpperCase(), true).stream()
                .map(SellStoreOptionResponse::from)
                .collect(Collectors.toList());
    }

    // =========================================================================
    // Procurement (admin)
    // =========================================================================

    /** Procurement queue — all requests (optionally filtered by status), newest first. */
    public List<SellRequestResponse> listAll(SellStatus status) {
        List<SellRequest> rows = (status != null)
                ? sellRepo.findByStatusOrderByCreatedAtDesc(status)
                : sellRepo.findAllByOrderByCreatedAtDesc();
        return rows.stream().map(this::toResponse).collect(Collectors.toList());
    }

    /** A single request for procurement. Opening it clears the "new update" badge flag. */
    @Transactional
    public SellRequestResponse getByIdAdmin(UUID id) {
        SellRequest request = loadOrThrow(id);
        if (request.isAdminUnread()) {
            request.setAdminUnread(false);
            request = sellRepo.save(request);
        }
        return toResponse(request);
    }

    /** Mark that the store received the device (SUBMITTED/AWAITING_DEVICE → UNDER_REVIEW). */
    @Transactional
    public SellRequestResponse markDeviceReceived(UUID id, UUID adminUserId) {
        SellRequest request = loadOrThrow(id);
        if (request.getStatus() != SellStatus.SUBMITTED && request.getStatus() != SellStatus.AWAITING_DEVICE) {
            throw new IllegalStateException("The device can only be received while the request is awaiting delivery.");
        }
        request.setStatus(SellStatus.UNDER_REVIEW);
        request.setDeviceReceivedAt(Instant.now());
        request.setUpdatedBy(adminUserId);
        request.setAdminUnread(false);
        request.setCustomerUnread(true);
        request = sellRepo.save(request);
        emailStatus(request, "We've received your device and our team is inspecting it.");
        return toResponse(request);
    }

    /**
     * Quote what Buyology will pay (UNDER_REVIEW / OFFER_MADE → OFFER_MADE) and email the customer.
     * Re-quoting a request that is already OFFER_MADE simply replaces the offer.
     */
    @Transactional
    public SellRequestResponse setOffer(UUID id, BigDecimal price, String currency, String validFor,
                                        DeviceCondition inspectedCondition, String note, UUID adminUserId) {
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("An offer greater than 0 is required.");
        }
        SellRequest request = loadOrThrow(id);
        if (request.getStatus() != SellStatus.UNDER_REVIEW && request.getStatus() != SellStatus.OFFER_MADE) {
            throw new IllegalStateException("An offer can only be made once the device is under review.");
        }
        request.setOfferPrice(price);
        request.setOfferPriceCurrency((currency == null || currency.isBlank())
                ? FEE_BASE_CURRENCY : currency.trim().toUpperCase());
        request.setOfferValidFor(validFor == null || validFor.isBlank() ? null : validFor.trim());
        if (inspectedCondition != null) request.setInspectedCondition(inspectedCondition);
        if (note != null && !note.isBlank()) request.setAdminNote(note.trim());
        request.setStatus(SellStatus.OFFER_MADE);
        request.setOfferedAt(Instant.now());
        request.setUpdatedBy(adminUserId);
        request.setAdminUnread(false);
        request.setCustomerUnread(true);
        request = sellRepo.save(request);

        final SellRequest saved = request;
        best("offer email", () -> emailService.sendSellOfferEmail(
                contactEmailFor(saved), resolveCustomerName(saved.getUserId()),
                saved.getReference(), saved.getProductName(),
                saved.getOfferPriceCurrency(), saved.getOfferPrice(),
                saved.getOfferValidFor(), saved.getAdminNote()));
        return toResponse(request);
    }

    /**
     * Record that the store handed the payout over (ACCEPTED → COMPLETED). The device is Buyology's
     * from this point and the ticket moves to history.
     */
    @Transactional
    public SellRequestResponse markPaidOut(UUID id, UUID adminUserId) {
        SellRequest request = loadOrThrow(id);
        if (request.getStatus() != SellStatus.ACCEPTED) {
            throw new IllegalStateException("Only an accepted offer can be paid out.");
        }
        request.setStatus(SellStatus.COMPLETED);
        request.setPaidOutAt(Instant.now());
        request.setUpdatedBy(adminUserId);
        request.setAdminUnread(false);
        request.setCustomerUnread(true);
        request = sellRepo.save(request);
        emailStatus(request, "Your payment has been handed over. Thanks for selling to Buyology!");
        return toResponse(request);
    }

    /** Generic status transition (e.g. mark CANCELLED) with an optional note. */
    @Transactional
    public SellRequestResponse updateStatus(UUID id, SellStatus status, String note, UUID adminUserId) {
        if (status == null) {
            throw new IllegalArgumentException("A status is required.");
        }
        SellRequest request = loadOrThrow(id);
        request.setStatus(status);
        if (status == SellStatus.COMPLETED && request.getPaidOutAt() == null) {
            request.setPaidOutAt(Instant.now());
        }
        if (note != null && !note.isBlank()) request.setAdminNote(note.trim());
        request.setUpdatedBy(adminUserId);
        request.setAdminUnread(false);
        request.setCustomerUnread(true);
        request = sellRepo.save(request);
        emailStatus(request, note);
        return toResponse(request);
    }

    /** Count of requests with unseen customer activity — drives the dashboard badge. */
    public long countUnread() {
        return sellRepo.countByAdminUnreadTrue();
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private SellRequest loadOrThrow(UUID id) {
        return sellRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Sell request not found: " + id));
    }

    private SellRequest requireOwner(SellRequest request, UUID userId) {
        UUID credentialId = resolveCredentialId(userId);
        if (request.getCredentialId() == null || !request.getCredentialId().equals(credentialId)) {
            throw new AccessDeniedException("This sell request does not belong to you.");
        }
        return request;
    }

    private StoreLocation requireStoreLocation(UUID storeLocationId) {
        if (storeLocationId == null) {
            throw new IllegalArgumentException("Please select a store branch.");
        }
        return storeLocationRepository.findById(storeLocationId)
                .filter(l -> Boolean.TRUE.equals(l.getIsActive()))
                .orElseThrow(() -> new IllegalArgumentException("The selected store branch is unavailable."));
    }

    /** Converts the 20 AED courier fee into the customer's currency (falls back to 20 AED). */
    private void applyCourierFee(SellRequest request, String currency) {
        String target = (currency == null || currency.isBlank()) ? FEE_BASE_CURRENCY : currency.trim().toUpperCase();
        BigDecimal amount = COURIER_FEE_AED;
        if (!FEE_BASE_CURRENCY.equals(target)) {
            try {
                amount = currencyExchangeService.convert(COURIER_FEE_AED, FEE_BASE_CURRENCY, target);
            } catch (Exception e) {
                log.warn("[SELL] courier fee conversion AED->{} failed, keeping AED: {}", target, e.getMessage());
                target = FEE_BASE_CURRENCY;
            }
        }
        request.setCourierFeeAmount(amount);
        request.setCourierFeeCurrency(target);
    }

    private String uploadImages(List<MultipartFile> images) {
        if (images == null || images.isEmpty()) return null;
        List<String> keys = new ArrayList<>();
        for (MultipartFile image : images) {
            if (image == null || image.isEmpty()) continue;
            if (keys.size() >= MAX_IMAGES) break;
            String filename = image.getOriginalFilename();
            if (filename == null || filename.isBlank()) filename = "image";
            String key = "sell-requests/" + UUID.randomUUID() + "/" + filename;
            keys.add(contaboObjectService.uploadFile(key, image));
        }
        return keys.isEmpty() ? null : String.join("\n", keys);
    }

    private SellRequestResponse toResponse(SellRequest request) {
        List<String> imageUrls = new ArrayList<>();
        if (request.getImageKeys() != null && !request.getImageKeys().isBlank()) {
            for (String key : request.getImageKeys().split("\n")) {
                if (key != null && !key.isBlank()) {
                    imageUrls.add(contaboObjectService.getPresignedUrl(key.trim()));
                }
            }
        }
        String branchName = null;
        String branchAddress = null;
        if (request.getStoreLocationId() != null) {
            StoreLocation branch = storeLocationRepository.findById(request.getStoreLocationId()).orElse(null);
            if (branch != null) {
                branchName = branch.getBranchName();
                branchAddress = branch.getAddress();
            }
        }
        return SellRequestResponse.from(request, imageUrls, branchName, branchAddress);
    }

    /**
     * As {@link #toResponse(SellRequest)}, plus the AI valuation converted from AED into
     * {@code displayCurrency} via {@link CurrencyExchangeService}. The AED figures stay on the
     * response either way — the converted pair is purely for display, and if the FX provider is
     * unreachable we simply omit it rather than fail the read.
     */
    private SellRequestResponse toResponse(SellRequest request, String displayCurrency) {
        SellRequestResponse dto = toResponse(request);
        if (displayCurrency == null || displayCurrency.isBlank()) {
            return dto;
        }
        String target = displayCurrency.trim().toUpperCase();
        if (target.equals(SellAiEstimateService.ESTIMATE_CURRENCY)
                || (dto.getAiEstimateMinPrice() == null && dto.getAiEstimateMaxPrice() == null)) {
            return dto;
        }
        try {
            dto.setAiEstimateConvertedMinPrice(currencyExchangeService.convert(
                    dto.getAiEstimateMinPrice(), SellAiEstimateService.ESTIMATE_CURRENCY, target));
            dto.setAiEstimateConvertedMaxPrice(currencyExchangeService.convert(
                    dto.getAiEstimateMaxPrice(), SellAiEstimateService.ESTIMATE_CURRENCY, target));
            dto.setAiEstimateConvertedCurrency(target);
        } catch (Exception e) {
            log.warn("[SELL-AI] Could not convert the valuation to {}; showing AED only.", target, e);
        }
        return dto;
    }

    private void emailStatus(SellRequest request, String note) {
        best("status email", () -> emailService.sendSellStatusEmail(
                contactEmailFor(request), resolveCustomerName(request.getUserId()),
                request.getReference(), request.getProductName(),
                stageLabel(request.getStatus()), note));
    }

    private String contactEmailFor(SellRequest request) {
        return request.getContactEmail() != null
                ? request.getContactEmail()
                : resolveContactEmail(request.getUserId(), request.getCredentialId());
    }

    private static String stageLabel(SellStatus status) {
        return switch (status) {
            case SUBMITTED -> "Received";
            case AWAITING_DEVICE -> "Awaiting device";
            case UNDER_REVIEW -> "Under inspection";
            case OFFER_MADE -> "Offer ready";
            case ACCEPTED -> "Offer accepted";
            case COMPLETED -> "Paid";
            case DECLINED -> "Offer declined";
            case CANCELLED -> "Cancelled";
        };
    }

    /** Display reference SR-{year}-{padded sequence}. Sequence is derived from the total count. */
    private String buildReference() {
        int year = LocalDate.now(ZoneOffset.UTC).getYear();
        long seq = sellRepo.count();
        return String.format("SR-%d-%03d", year, seq);
    }

    /**
     * Resolve the caller's auth_credentials.id (sub) from the users.id principal — the request
     * owner is keyed on the credential. Prefers the LOCAL credential, else the first, else falls
     * back to the principal itself if it already is a credential id.
     */
    private UUID resolveCredentialId(UUID userId) {
        List<AuthCredentials> creds = authCredentialRepository.findByUserId(userId);
        if (!creds.isEmpty()) {
            return creds.stream()
                    .filter(c -> "LOCAL".equalsIgnoreCase(c.getProvider()))
                    .map(AuthCredentials::getId)
                    .findFirst()
                    .orElse(creds.get(0).getId());
        }
        return authCredentialRepository.findById(userId).map(AuthCredentials::getId).orElse(userId);
    }

    /** users.id from a principal that may already be users.id, or an auth_credentials.id. */
    private UUID resolveUsersId(UUID candidate) {
        if (candidate == null) return null;
        if (userRepository.existsById(candidate)) return candidate;
        return authCredentialRepository.findById(candidate)
                .map(AuthCredentials::getUserId)
                .orElse(candidate);
    }

    private String resolveContactEmail(UUID userId, UUID credentialId) {
        if (credentialId != null) {
            String email = authCredentialRepository.findById(credentialId)
                    .map(AuthCredentials::getEmail)
                    .filter(e -> e != null && !e.isBlank())
                    .orElse(null);
            if (email != null) return email;
        }
        if (userId != null) {
            return authCredentialRepository.findByUserId(userId).stream()
                    .map(AuthCredentials::getEmail)
                    .filter(e -> e != null && !e.isBlank())
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    private String resolveContactPhone(UUID userId) {
        if (userId == null) return null;
        return userProfilesRepository.findByUserId(userId)
                .map(p -> p.getPhoneNumber())
                .filter(p -> p != null && !p.isBlank())
                .orElse(null);
    }

    private String resolveCustomerName(UUID userId) {
        if (userId == null) return null;
        return userRepository.findById(userId)
                .map(this::fullName)
                .filter(n -> n != null && !n.isBlank())
                .orElse(null);
    }

    private String fullName(Users u) {
        String first = u.getFirstName() == null ? "" : u.getFirstName().trim();
        String last = u.getLastName() == null ? "" : u.getLastName().trim();
        return (first + " " + last).trim();
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
    }

    /** A device can't be valued without a photo of it, so at least one is mandatory. */
    private static void requireAtLeastOneImage(List<MultipartFile> images) {
        boolean hasUsableImage = images != null
                && images.stream().anyMatch(image -> image != null && !image.isEmpty());
        if (!hasUsableImage) {
            throw new IllegalArgumentException("At least one photo of the device is required.");
        }
    }

    /** Runs a best-effort side effect (email); logs but never propagates failure. */
    private void best(String what, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.warn("[SELL] {} failed: {}", what, e.getMessage());
        }
    }
}
