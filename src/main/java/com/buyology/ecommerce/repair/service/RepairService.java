package com.buyology.ecommerce.repair.service;

import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import com.buyology.ecommerce.common.service.EmailService;
import com.buyology.ecommerce.currency.service.CurrencyExchangeService;
import com.buyology.ecommerce.infrastructure.external.ContaboObjectService;
import com.buyology.ecommerce.payment.dto.CourierFeeChargeRequest;
import com.buyology.ecommerce.payment.dto.PaymentInitiatedResponse;
import com.buyology.ecommerce.payment.event.RepairCourierFeePaidEvent;
import com.buyology.ecommerce.payment.service.PaymentService;
import com.buyology.ecommerce.repair.domain.RepairDeliveryMethod;
import com.buyology.ecommerce.repair.domain.RepairRequest;
import com.buyology.ecommerce.repair.domain.RepairStatus;
import com.buyology.ecommerce.repair.dto.RepairDeliveryResponse;
import com.buyology.ecommerce.repair.dto.RepairRequestResponse;
import com.buyology.ecommerce.repair.dto.StoreLocationOptionResponse;
import com.buyology.ecommerce.repair.event.RepairSubmittedEvent;
import com.buyology.ecommerce.repair.repository.RepairRequestRepository;
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
 * Customer device-repair service.
 *
 * Any logged-in customer opens a repair request (no B2B membership gate). Ownership is keyed on
 * the caller's auth_credentials.id (sub); the JWT filter sets the principal to users.id (uid), so
 * the service resolves the credential. The repair team (SUPERADMIN / REPAIR) triages it through
 * SUBMITTED → AWAITING_DEVICE → UNDER_REVIEW → PRICE_ESTIMATED → IN_REPAIR → COMPLETED (plus
 * DECLINED / CANCELLED). Contact email/phone are snapshotted from the profile at submit time.
 * The customer is emailed on submit and every team update; the dashboard badge is driven by the
 * {@code adminUnread} flag which every customer-side action re-raises.
 */
@Service
public class RepairService {

    private static final Logger log = LoggerFactory.getLogger(RepairService.class);

    /** Base courier fee (converted to the customer's currency for display/charge). */
    public static final BigDecimal COURIER_FEE_AED = new BigDecimal("20");
    private static final String FEE_BASE_CURRENCY = "AED";
    private static final int MAX_IMAGES = 4;

    private final RepairRequestRepository repairRepo;
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
    private String repairTeamEmail;

    @Value("${app.web-base-url:https://buyology.online}")
    private String webBaseUrl;

    public RepairService(RepairRequestRepository repairRepo,
                         AuthCredentialRepository authCredentialRepository,
                         UserRepository userRepository,
                         UserProfilesRepository userProfilesRepository,
                         StoreLocationRepository storeLocationRepository,
                         ContaboObjectService contaboObjectService,
                         CurrencyExchangeService currencyExchangeService,
                         EmailService emailService,
                         PaymentService paymentService,
                         ApplicationEventPublisher eventPublisher) {
        this.repairRepo = repairRepo;
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
     * Open a new repair request. Product name / brand / model / description are required;
     * purchase date is optional. Up to {@link #MAX_IMAGES} problem images are uploaded to Contabo
     * under {@code repairs/{uuid}/{filename}} (only the keys are persisted). Contact email/phone
     * are snapshotted from the caller's profile. The customer is emailed a confirmation and the
     * repair team is notified — both best-effort.
     */
    @Transactional
    public RepairRequestResponse create(UUID userId, String productName, String brand, String model,
                                        LocalDate purchaseDate, String description, List<MultipartFile> images) {
        UUID credentialId = resolveCredentialId(userId);
        UUID resolvedUserId = resolveUsersId(userId);

        requireText(productName, "Product name");
        requireText(brand, "Brand");
        requireText(model, "Model");
        requireText(description, "Problem description");
        // At least one photo is mandatory: the team (and the AI estimate) price the repair from
        // what the photos show, so a request without one can't be assessed. Checked here as well
        // as in the browser because the client-side check is trivially bypassable.
        requireAtLeastOneImage(images);

        String imageKeys = uploadImages(images);
        if (imageKeys == null) {
            throw new IllegalArgumentException("At least one photo of the problem is required.");
        }
        String email = resolveContactEmail(resolvedUserId, credentialId);
        String phone = resolveContactPhone(resolvedUserId);
        String name = resolveCustomerName(resolvedUserId);

        RepairRequest request = new RepairRequest();
        request.setCredentialId(credentialId);
        request.setUserId(resolvedUserId);
        request.setProductName(productName.trim());
        request.setBrand(brand.trim());
        request.setModel(model.trim());
        request.setPurchaseDate(purchaseDate);
        request.setDescription(description.trim());
        request.setImageKeys(imageKeys);
        request.setStatus(RepairStatus.SUBMITTED);
        request.setContactEmail(email);
        request.setContactPhone(phone);
        request.setAdminUnread(true);
        request.setCustomerUnread(false);
        request.setSubmittedAt(Instant.now());
        request = repairRepo.save(request);
        request.setReference(buildReference(request));
        request = repairRepo.save(request);

        final RepairRequest saved = request;
        best("received email", () -> {
            if (email != null && !email.isBlank()) {
                emailService.sendRepairReceivedEmail(email, name, saved.getReference(), saved.getProductName());
            }
        });
        best("team notification", () -> emailService.sendRepairTeamNotificationEmail(
                repairTeamEmail, name, saved.getReference(), saved.getProductName(),
                saved.getBrand(), saved.getModel(), saved.getDescription(),
                webBaseUrl + "/repair/" + saved.getId()));

        // Kicks off the advisory AI price estimate. Consumed AFTER_COMMIT on another thread, so it
        // neither delays this response nor can it fail the submission.
        eventPublisher.publishEvent(new RepairSubmittedEvent(saved.getId()));

        return toResponse(request);
    }

    /** A customer's own repairs, newest first. */
    public List<RepairRequestResponse> listOwn(UUID userId) {
        return listOwn(userId, null);
    }

    /**
     * A customer's own repairs, newest first. When {@code displayCurrency} is supplied, the AED AI
     * estimate is additionally converted into it for display.
     */
    public List<RepairRequestResponse> listOwn(UUID userId, String displayCurrency) {
        UUID credentialId = resolveCredentialId(userId);
        return repairRepo.findByCredentialIdOrderByCreatedAtDesc(credentialId).stream()
                .map(r -> toResponse(r, displayCurrency))
                .collect(Collectors.toList());
    }

    /** A single owned repair. Opening it clears the customer's "new update" flag. */
    @Transactional
    public RepairRequestResponse getOwn(UUID userId, UUID id) {
        return getOwn(userId, id, null);
    }

    /**
     * A single owned repair, with the AI estimate converted into {@code displayCurrency} when given.
     * Opening it clears the customer's "new update" flag.
     */
    @Transactional
    public RepairRequestResponse getOwn(UUID userId, UUID id, String displayCurrency) {
        RepairRequest request = requireOwner(loadOrThrow(id), userId);
        if (request.isCustomerUnread()) {
            request.setCustomerUnread(false);
            request = repairRepo.save(request);
        }
        return toResponse(request, displayCurrency);
    }

    /**
     * Choose how the device reaches the store (only valid while SUBMITTED). STORE_DROPOFF requires
     * a store branch and is free — the request advances to AWAITING_DEVICE immediately.
     * COURIER_PICKUP records the 20 AED fee and returns a Paymob checkout session: the request
     * stays SUBMITTED (courier method recorded, unpaid) until the fee is paid, when the
     * {@link RepairCourierFeePaidEvent} webhook advances it to AWAITING_DEVICE.
     */
    @Transactional
    public RepairDeliveryResponse chooseDelivery(UUID userId, UUID id, RepairDeliveryMethod method,
                                                 UUID storeLocationId, String currency, String redirectionUrl) {
        RepairRequest request = requireOwner(loadOrThrow(id), userId);
        if (request.getStatus() != RepairStatus.SUBMITTED) {
            throw new IllegalStateException("Delivery can only be chosen while the request is awaiting your choice.");
        }
        if (method != RepairDeliveryMethod.COURIER_PICKUP && method != RepairDeliveryMethod.STORE_DROPOFF) {
            throw new IllegalArgumentException("Inbound delivery must be COURIER_PICKUP or STORE_DROPOFF.");
        }

        request.setInboundDeliveryMethod(method);
        if (method == RepairDeliveryMethod.STORE_DROPOFF) {
            StoreLocation branch = requireStoreLocation(storeLocationId);
            request.setStoreLocationId(branch.getId());
            request.setCourierFeeAmount(null);
            request.setCourierFeeCurrency(null);
            request.setCourierFeePaid(false);
            request.setStatus(RepairStatus.AWAITING_DEVICE);
            request.setAdminUnread(true);
            request = repairRepo.save(request);
            return new RepairDeliveryResponse(toResponse(request), null);
        }

        // Courier pickup — the customer pays the fee first; the request stays SUBMITTED (unpaid).
        request.setStoreLocationId(null);
        applyCourierFee(request, currency);
        request.setCourierFeePaid(false);
        request = repairRepo.save(request);
        PaymentInitiatedResponse payment = initiateRepairCourierFee(request, redirectionUrl);
        return new RepairDeliveryResponse(toResponse(request), payment);
    }

    /**
     * Customer's decision on the quoted price (only valid while PRICE_ESTIMATED). Accept →
     * IN_REPAIR (repair starts, team notified); decline → DECLINED (device must be returned).
     */
    @Transactional
    public RepairRequestResponse respondToPrice(UUID userId, UUID id, boolean accept) {
        RepairRequest request = requireOwner(loadOrThrow(id), userId);
        if (request.getStatus() != RepairStatus.PRICE_ESTIMATED) {
            throw new IllegalStateException("There is no price estimate awaiting your response.");
        }
        request.setStatus(accept ? RepairStatus.IN_REPAIR : RepairStatus.DECLINED);
        request.setAdminUnread(true);
        request = repairRepo.save(request);

        final RepairRequest saved = request;
        best("price decision email", () -> {
            String email = contactEmailFor(saved);
            if (email != null && !email.isBlank()) {
                emailService.sendRepairStatusEmail(email, resolveCustomerName(saved.getUserId()),
                        saved.getReference(), saved.getProductName(),
                        stageLabel(saved.getStatus()),
                        accept ? "Thanks for confirming — our team is starting the repair."
                               : "You've declined the estimate. Please choose how to get your device back.");
            }
        });
        return toResponse(request);
    }

    /**
     * After a decline, choose how the device is returned (only valid while DECLINED). STORE_PICKUP
     * is free and arranges the return immediately. COURIER_RETURN records the 20 AED fee and
     * returns a Paymob checkout session; the return is only arranged once the fee is paid (via the
     * {@link RepairCourierFeePaidEvent} webhook).
     */
    @Transactional
    public RepairDeliveryResponse chooseReturn(UUID userId, UUID id, RepairDeliveryMethod method,
                                               String currency, String redirectionUrl) {
        RepairRequest request = requireOwner(loadOrThrow(id), userId);
        if (request.getStatus() != RepairStatus.DECLINED) {
            throw new IllegalStateException("A return can only be arranged after declining an estimate.");
        }
        if (method != RepairDeliveryMethod.COURIER_RETURN && method != RepairDeliveryMethod.STORE_PICKUP) {
            throw new IllegalArgumentException("Return delivery must be COURIER_RETURN or STORE_PICKUP.");
        }
        request.setReturnDeliveryMethod(method);
        if (method == RepairDeliveryMethod.STORE_PICKUP) {
            request.setCourierFeeAmount(null);
            request.setCourierFeeCurrency(null);
            request.setCourierFeePaid(false);
            request.setAdminUnread(true);
            request = repairRepo.save(request);
            return new RepairDeliveryResponse(toResponse(request), null);
        }

        // Courier return — the customer pays the fee first; the return is arranged when it clears.
        applyCourierFee(request, currency);
        request.setCourierFeePaid(false);
        request = repairRepo.save(request);
        PaymentInitiatedResponse payment = initiateRepairCourierFee(request, redirectionUrl);
        return new RepairDeliveryResponse(toResponse(request), payment);
    }

    /**
     * Start a Paymob card charge for this repair's 20 AED courier fee (settlement in AED). On
     * success the {@link RepairCourierFeePaidEvent} webhook calls {@link #onRepairCourierFeePaid}.
     */
    private PaymentInitiatedResponse initiateRepairCourierFee(RepairRequest request, String redirectionUrl) {
        String name = resolveCustomerName(request.getUserId());
        CourierFeeChargeRequest charge = new CourierFeeChargeRequest(
                null,                        // refundRequestId — this is a repair fee
                request.getId(),             // repairId
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
     * Advances a repair once its courier fee clears (Paymob webhook, AFTER_COMMIT). Confirms the
     * inbound pickup (SUBMITTED → AWAITING_DEVICE) or the post-decline return. Idempotent.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRepairCourierFeePaid(RepairCourierFeePaidEvent event) {
        RepairRequest request = repairRepo.findById(event.repairId()).orElse(null);
        if (request == null) {
            log.warn("[REPAIR] RepairCourierFeePaidEvent for unknown repair {}", event.repairId());
            return;
        }
        if (request.isCourierFeePaid()) {
            log.info("[REPAIR] RepairCourierFeePaidEvent for repair {} ignored (already paid)", request.getId());
            return;
        }
        if (request.getStatus() == RepairStatus.SUBMITTED
                && request.getInboundDeliveryMethod() == RepairDeliveryMethod.COURIER_PICKUP) {
            request.setCourierFeePaid(true);
            request.setStatus(RepairStatus.AWAITING_DEVICE);
            request.setAdminUnread(true);
            repairRepo.save(request);
            emailStatus(request, "Your courier pickup is arranged — we'll collect your device shortly.");
            log.info("[REPAIR] Courier pickup fee paid — repair {} advanced to AWAITING_DEVICE", request.getId());
        } else if (request.getStatus() == RepairStatus.DECLINED
                && request.getReturnDeliveryMethod() == RepairDeliveryMethod.COURIER_RETURN) {
            request.setCourierFeePaid(true);
            request.setAdminUnread(true);
            repairRepo.save(request);
            emailStatus(request, "Your courier return is arranged — we'll deliver your device back to you.");
            log.info("[REPAIR] Courier return fee paid — repair {} return arranged", request.getId());
        } else {
            log.info("[REPAIR] RepairCourierFeePaidEvent for repair {} ignored (status {}, inbound {}, return {})",
                    request.getId(), request.getStatus(), request.getInboundDeliveryMethod(),
                    request.getReturnDeliveryMethod());
        }
    }

    /** Active store branches in a country (alpha-3 code) for the drop-off / pickup picker. */
    public List<StoreLocationOptionResponse> listStoreOptions(String country) {
        if (country == null || country.isBlank()) {
            return List.of();
        }
        return storeLocationRepository.findAllByCountryAndIsActive(country.trim().toUpperCase(), true).stream()
                .map(StoreLocationOptionResponse::from)
                .collect(Collectors.toList());
    }

    // =========================================================================
    // Repair team (admin)
    // =========================================================================

    /** Repair-team queue — all requests (optionally filtered by status), newest first. */
    public List<RepairRequestResponse> listAll(RepairStatus status) {
        List<RepairRequest> rows = (status != null)
                ? repairRepo.findByStatusOrderByCreatedAtDesc(status)
                : repairRepo.findAllByOrderByCreatedAtDesc();
        return rows.stream().map(this::toResponse).collect(Collectors.toList());
    }

    /** A single request for the team. Opening it clears the "new update" badge flag. */
    @Transactional
    public RepairRequestResponse getByIdAdmin(UUID id) {
        RepairRequest request = loadOrThrow(id);
        if (request.isAdminUnread()) {
            request.setAdminUnread(false);
            request = repairRepo.save(request);
        }
        return toResponse(request);
    }

    /** Mark that the store received the device (SUBMITTED/AWAITING_DEVICE → UNDER_REVIEW). */
    @Transactional
    public RepairRequestResponse markDeviceReceived(UUID id, UUID adminUserId) {
        RepairRequest request = loadOrThrow(id);
        if (request.getStatus() != RepairStatus.SUBMITTED && request.getStatus() != RepairStatus.AWAITING_DEVICE) {
            throw new IllegalStateException("The device can only be received while the request is awaiting delivery.");
        }
        request.setStatus(RepairStatus.UNDER_REVIEW);
        request.setDeviceReceivedAt(Instant.now());
        request.setUpdatedBy(adminUserId);
        request.setAdminUnread(false);
        request.setCustomerUnread(true);
        request = repairRepo.save(request);
        emailStatus(request, "We've received your device and our team is reviewing it.");
        return toResponse(request);
    }

    /** Quote the fixing price (UNDER_REVIEW / PRICE_ESTIMATED → PRICE_ESTIMATED) and email the customer. */
    @Transactional
    public RepairRequestResponse setPrice(UUID id, BigDecimal price, String currency,
                                          String estimatedTime, String note, UUID adminUserId) {
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("A price greater than 0 is required.");
        }
        RepairRequest request = loadOrThrow(id);
        if (request.getStatus() != RepairStatus.UNDER_REVIEW && request.getStatus() != RepairStatus.PRICE_ESTIMATED) {
            throw new IllegalStateException("A price can only be set once the device is under review.");
        }
        request.setEstimatedPrice(price);
        request.setEstimatedPriceCurrency((currency == null || currency.isBlank()) ? FEE_BASE_CURRENCY : currency.trim().toUpperCase());
        request.setEstimatedTime(estimatedTime == null || estimatedTime.isBlank() ? null : estimatedTime.trim());
        if (note != null && !note.isBlank()) request.setAdminNote(note.trim());
        request.setStatus(RepairStatus.PRICE_ESTIMATED);
        request.setPricedAt(Instant.now());
        request.setUpdatedBy(adminUserId);
        request.setAdminUnread(false);
        request.setCustomerUnread(true);
        request = repairRepo.save(request);

        final RepairRequest saved = request;
        best("price email", () -> {
            String email = contactEmailFor(saved);
            if (email != null && !email.isBlank()) {
                emailService.sendRepairPriceEmail(email, resolveCustomerName(saved.getUserId()),
                        saved.getReference(), saved.getProductName(),
                        saved.getEstimatedPriceCurrency(), saved.getEstimatedPrice(),
                        saved.getEstimatedTime(), saved.getAdminNote());
            }
        });
        return toResponse(request);
    }

    /** Generic status transition (e.g. mark COMPLETED / CANCELLED) with an optional note. */
    @Transactional
    public RepairRequestResponse updateStatus(UUID id, RepairStatus status, String note, UUID adminUserId) {
        if (status == null) {
            throw new IllegalArgumentException("A status is required.");
        }
        RepairRequest request = loadOrThrow(id);
        request.setStatus(status);
        if (note != null && !note.isBlank()) request.setAdminNote(note.trim());
        request.setUpdatedBy(adminUserId);
        request.setAdminUnread(false);
        request.setCustomerUnread(true);
        request = repairRepo.save(request);
        emailStatus(request, note);
        return toResponse(request);
    }

    /** Count of requests with unseen customer activity — drives the dashboard badge. */
    public long countUnread() {
        return repairRepo.countByAdminUnreadTrue();
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private RepairRequest loadOrThrow(UUID id) {
        return repairRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Repair request not found: " + id));
    }

    private RepairRequest requireOwner(RepairRequest request, UUID userId) {
        UUID credentialId = resolveCredentialId(userId);
        if (request.getCredentialId() == null || !request.getCredentialId().equals(credentialId)) {
            throw new AccessDeniedException("This repair request does not belong to you.");
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
    private void applyCourierFee(RepairRequest request, String currency) {
        String target = (currency == null || currency.isBlank()) ? FEE_BASE_CURRENCY : currency.trim().toUpperCase();
        BigDecimal amount = COURIER_FEE_AED;
        if (!FEE_BASE_CURRENCY.equals(target)) {
            try {
                amount = currencyExchangeService.convert(COURIER_FEE_AED, FEE_BASE_CURRENCY, target);
            } catch (Exception e) {
                log.warn("[REPAIR] courier fee conversion AED->{} failed, keeping AED: {}", target, e.getMessage());
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
            String key = "repairs/" + UUID.randomUUID() + "/" + filename;
            keys.add(contaboObjectService.uploadFile(key, image));
        }
        return keys.isEmpty() ? null : String.join("\n", keys);
    }

    private RepairRequestResponse toResponse(RepairRequest request) {
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
        return RepairRequestResponse.from(request, imageUrls, branchName, branchAddress);
    }

    /**
     * As {@link #toResponse(RepairRequest)}, plus the AI estimate converted from AED into
     * {@code displayCurrency} via {@link CurrencyExchangeService}. The AED figures stay on the
     * response either way — the converted pair is purely for display, and if the FX provider is
     * unreachable we simply omit it rather than fail the read.
     */
    private RepairRequestResponse toResponse(RepairRequest request, String displayCurrency) {
        RepairRequestResponse dto = toResponse(request);
        if (displayCurrency == null || displayCurrency.isBlank()) {
            return dto;
        }
        String target = displayCurrency.trim().toUpperCase();
        if (target.equals(RepairAiEstimateService.ESTIMATE_CURRENCY)
                || (dto.getAiEstimateMinPrice() == null && dto.getAiEstimateMaxPrice() == null)) {
            return dto;
        }
        try {
            dto.setAiEstimateConvertedMinPrice(currencyExchangeService.convert(
                    dto.getAiEstimateMinPrice(), RepairAiEstimateService.ESTIMATE_CURRENCY, target));
            dto.setAiEstimateConvertedMaxPrice(currencyExchangeService.convert(
                    dto.getAiEstimateMaxPrice(), RepairAiEstimateService.ESTIMATE_CURRENCY, target));
            dto.setAiEstimateConvertedCurrency(target);
        } catch (Exception e) {
            log.warn("[REPAIR-AI] Could not convert the estimate to {}; showing AED only.", target, e);
        }
        return dto;
    }

    private void emailStatus(RepairRequest request, String note) {
        best("status email", () -> {
            String email = contactEmailFor(request);
            if (email != null && !email.isBlank()) {
                emailService.sendRepairStatusEmail(email, resolveCustomerName(request.getUserId()),
                        request.getReference(), request.getProductName(),
                        stageLabel(request.getStatus()), note);
            }
        });
    }

    private String contactEmailFor(RepairRequest request) {
        return request.getContactEmail() != null
                ? request.getContactEmail()
                : resolveContactEmail(request.getUserId(), request.getCredentialId());
    }

    private static String stageLabel(RepairStatus status) {
        return switch (status) {
            case SUBMITTED -> "Received";
            case AWAITING_DEVICE -> "Awaiting device";
            case UNDER_REVIEW -> "Under review";
            case PRICE_ESTIMATED -> "Price estimate ready";
            case IN_REPAIR -> "Repair started";
            case COMPLETED -> "Completed";
            case DECLINED -> "Estimate declined";
            case CANCELLED -> "Cancelled";
        };
    }

    /** Display reference RR-{year}-{padded sequence}. Sequence is derived from the total count. */
    private String buildReference(RepairRequest request) {
        int year = LocalDate.now(ZoneOffset.UTC).getYear();
        long seq = repairRepo.count();
        return String.format("RR-%d-%03d", year, seq);
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

    /** A repair can't be assessed without a photo of the fault, so at least one is mandatory. */
    private static void requireAtLeastOneImage(List<MultipartFile> images) {
        boolean hasUsableImage = images != null
                && images.stream().anyMatch(image -> image != null && !image.isEmpty());
        if (!hasUsableImage) {
            throw new IllegalArgumentException("At least one photo of the problem is required.");
        }
    }

    /** Runs a best-effort side effect (email); logs but never propagates failure. */
    private void best(String what, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.warn("[REPAIR] {} failed: {}", what, e.getMessage());
        }
    }
}
