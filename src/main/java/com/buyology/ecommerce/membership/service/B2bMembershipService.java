package com.buyology.ecommerce.membership.service;

import com.buyology.ecommerce.common.service.EmailService;
import com.buyology.ecommerce.membership.domain.B2bMembership;
import com.buyology.ecommerce.membership.domain.B2bMembershipApplication;
import com.buyology.ecommerce.membership.domain.Wallet;
import com.buyology.ecommerce.membership.dto.*;
import com.buyology.ecommerce.membership.repository.B2bMembershipApplicationRepository;
import com.buyology.ecommerce.membership.repository.B2bMembershipRepository;
import com.buyology.ecommerce.membership.repository.WalletRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Year;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class B2bMembershipService {

    private static final Logger log = LoggerFactory.getLogger(B2bMembershipService.class);
    private static final AtomicInteger SEQ = new AtomicInteger(1000);

    private final B2bMembershipApplicationRepository appRepo;
    private final B2bMembershipRepository membershipRepo;
    private final WalletRepository walletRepo;
    private final WalletService walletService;
    private final EmailService emailService;

    public B2bMembershipService(B2bMembershipApplicationRepository appRepo,
                                 B2bMembershipRepository membershipRepo,
                                 WalletRepository walletRepo,
                                 WalletService walletService,
                                 EmailService emailService) {
        this.appRepo = appRepo;
        this.membershipRepo = membershipRepo;
        this.walletRepo = walletRepo;
        this.walletService = walletService;
        this.emailService = emailService;
    }

    // ── Customer endpoints ───────────────────────────────────────────────────

    @Transactional
    public MembershipApplicationResponse submitApplication(MembershipApplicationRequest req, UUID userId) {
        B2bMembershipApplication app = new B2bMembershipApplication();
        app.setUserId(userId);
        app.setCompanyName(req.getCompanyName());
        app.setTradeLicenseNumber(req.getTradeLicenseNumber());
        app.setIndustryType(req.getIndustryType());
        app.setNumberOfEmployees(req.getNumberOfEmployees());
        app.setCountry(req.getCountry());
        app.setCity(req.getCity());
        app.setWebsite(req.getWebsite());
        app.setContactFullName(req.getContactFullName());
        app.setContactDesignation(req.getContactDesignation());
        app.setContactEmail(req.getContactEmail());
        app.setContactMobile(req.getContactMobile());
        app.setTermsAccepted(req.isTermsAccepted());
        if (req.getBusinessNeeds() != null) {
            app.setBusinessNeeds(String.join(",", req.getBusinessNeeds()));
        }
        app = appRepo.save(app);

        try {
            emailService.sendB2bInquiryNotification(
                    "firdovsirz@gmail.com",
                    app.getCompanyName(), app.getContactFullName(),
                    app.getContactEmail(), app.getContactMobile(),
                    0, "B2B Membership Application submitted - Status: PENDING");
        } catch (Exception e) {
            log.warn("Admin notification failed: {}", e.getMessage());
        }

        return toAppResponse(app);
    }

    public MembershipApplicationResponse getMyApplication(UUID userId) {
        B2bMembershipApplication app = appRepo.findByUserId(userId)
                .orElseThrow(() -> new NoSuchElementException("No application found for user"));
        return toAppResponse(app);
    }

    public MembershipCardResponse getMembershipCard(UUID userId) {
        B2bMembership membership = membershipRepo.findByUserId(userId)
                .orElseThrow(() -> new NoSuchElementException("No active membership found"));

        Optional<Wallet> wallet = walletRepo.findByUserId(userId);
        return toCardResponse(membership, wallet.orElse(null));
    }

    // ── Admin endpoints ──────────────────────────────────────────────────────

    public List<MembershipApplicationResponse> listAllApplications() {
        return appRepo.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toAppResponse).collect(Collectors.toList());
    }

    @Transactional
    public MembershipApplicationResponse processAction(UUID appId, ApplicationActionRequest req) {
        B2bMembershipApplication app = appRepo.findById(appId)
                .orElseThrow(() -> new NoSuchElementException("Application not found: " + appId));

        String action = req.getAction().toUpperCase();
        Instant now = Instant.now();

        switch (action) {
            case "APPROVE" -> {
                app.setStatus(B2bMembershipApplication.ApplicationStatus.APPROVED);
                app.setApprovedBy(req.getPerformedBy());
                app.setApprovedAt(now);
                app = appRepo.save(app);
                activateMembership(app, req.getPerformedBy());
            }
            case "REJECT" -> {
                if (req.getRejectionReason() == null || req.getRejectionReason().isBlank()) {
                    throw new IllegalArgumentException("Rejection reason is required");
                }
                app.setStatus(B2bMembershipApplication.ApplicationStatus.REJECTED);
                app.setRejectionReason(req.getRejectionReason());
                app.setRejectedBy(req.getPerformedBy());
                app.setRejectedAt(now);
                app = appRepo.save(app);
                sendRejectionEmail(app);
            }
            case "UNDER_REVIEW" -> {
                app.setStatus(B2bMembershipApplication.ApplicationStatus.UNDER_REVIEW);
                app.setReviewedBy(req.getPerformedBy());
                app.setReviewedAt(now);
                app = appRepo.save(app);
            }
            default -> throw new IllegalArgumentException("Unknown action: " + action);
        }

        return toAppResponse(app);
    }

    public List<MembershipCardResponse> listAllMemberships() {
        return membershipRepo.findAllByOrderByCreatedAtDesc().stream()
                .map(m -> {
                    Optional<Wallet> w = walletRepo.findByUserId(m.getUserId());
                    return toCardResponse(m, w.orElse(null));
                })
                .collect(Collectors.toList());
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private void activateMembership(B2bMembershipApplication app, String performedBy) {
        if (app.getUserId() == null) return;

        if (!membershipRepo.existsByUserId(app.getUserId())) {
            B2bMembership membership = new B2bMembership();
            membership.setMembershipId(generateMembershipId());
            membership.setUserId(app.getUserId());
            membership.setApplicationId(app.getId());
            membership.setCompanyName(app.getCompanyName());
            membership.setMemberName(app.getContactFullName());
            membership.setStatus(B2bMembership.MembershipStatus.ACTIVE);
            membership.setTier(B2bMembership.MembershipTier.PREMIUM);
            membershipRepo.save(membership);
        }

        walletService.addInitialCredit(app.getUserId(), performedBy);

        try {
            emailService.sendB2bInquiryNotification(
                    app.getContactEmail(),
                    app.getCompanyName(), app.getContactFullName(),
                    app.getContactEmail(), app.getContactMobile(),
                    0, "Congratulations! Your B2B Premium membership has been approved. You have received AED 5,000 wallet credit.");
        } catch (Exception e) {
            log.warn("Approval email failed: {}", e.getMessage());
        }
    }

    private void sendRejectionEmail(B2bMembershipApplication app) {
        try {
            emailService.sendB2bInquiryNotification(
                    app.getContactEmail(),
                    app.getCompanyName(), app.getContactFullName(),
                    app.getContactEmail(), app.getContactMobile(),
                    0, "Your B2B membership application has been reviewed. Reason: " + app.getRejectionReason());
        } catch (Exception e) {
            log.warn("Rejection email failed: {}", e.getMessage());
        }
    }

    private String generateMembershipId() {
        return "BUY-" + Year.now().getValue() + "-" + SEQ.getAndIncrement();
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private MembershipApplicationResponse toAppResponse(B2bMembershipApplication a) {
        MembershipApplicationResponse r = new MembershipApplicationResponse();
        r.setId(a.getId());
        r.setUserId(a.getUserId());
        r.setCompanyName(a.getCompanyName());
        r.setTradeLicenseNumber(a.getTradeLicenseNumber());
        r.setIndustryType(a.getIndustryType());
        r.setNumberOfEmployees(a.getNumberOfEmployees());
        r.setCountry(a.getCountry());
        r.setCity(a.getCity());
        r.setWebsite(a.getWebsite());
        r.setContactFullName(a.getContactFullName());
        r.setContactDesignation(a.getContactDesignation());
        r.setContactEmail(a.getContactEmail());
        r.setContactMobile(a.getContactMobile());
        r.setTermsAccepted(a.isTermsAccepted());
        r.setTradeLicenseFileUrl(a.getTradeLicenseFileUrl());
        r.setVatCertificateFileUrl(a.getVatCertificateFileUrl());
        r.setStatus(a.getStatus());
        r.setRejectionReason(a.getRejectionReason());
        r.setRejectedBy(a.getRejectedBy());
        r.setRejectedAt(a.getRejectedAt());
        r.setReviewedBy(a.getReviewedBy());
        r.setReviewedAt(a.getReviewedAt());
        r.setApprovedBy(a.getApprovedBy());
        r.setApprovedAt(a.getApprovedAt());
        r.setCreatedAt(a.getCreatedAt());
        r.setUpdatedAt(a.getUpdatedAt());
        if (a.getBusinessNeeds() != null && !a.getBusinessNeeds().isBlank()) {
            r.setBusinessNeeds(Arrays.asList(a.getBusinessNeeds().split(",")));
        }
        return r;
    }

    private MembershipCardResponse toCardResponse(B2bMembership m, Wallet wallet) {
        MembershipCardResponse r = new MembershipCardResponse();
        r.setId(m.getId());
        r.setMembershipId(m.getMembershipId());
        r.setUserId(m.getUserId());
        r.setCompanyName(m.getCompanyName());
        r.setMemberName(m.getMemberName());
        r.setStatus(m.getStatus());
        r.setTier(m.getTier());
        r.setValidUntil(m.getValidUntil());
        r.setCreatedAt(m.getCreatedAt());
        r.setQrCodePlaceholder("QR:" + m.getMembershipId());
        if (wallet != null) {
            r.setWalletBalance(wallet.getBalance());
            r.setWalletCurrency(wallet.getCurrency());
        }
        return r;
    }
}
