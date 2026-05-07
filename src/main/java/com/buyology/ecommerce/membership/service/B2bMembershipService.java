package com.buyology.ecommerce.membership.service;

import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import com.buyology.ecommerce.common.service.EmailService;
import com.buyology.ecommerce.membership.domain.B2bCountry;
import com.buyology.ecommerce.membership.domain.B2bMembership;
import com.buyology.ecommerce.membership.domain.B2bMembershipApplication;
import com.buyology.ecommerce.membership.domain.B2bSetupToken;
import com.buyology.ecommerce.membership.domain.Wallet;
import com.buyology.ecommerce.membership.dto.*;
import com.buyology.ecommerce.membership.repository.B2bCountryRepository;
import com.buyology.ecommerce.membership.repository.B2bMembershipApplicationRepository;
import com.buyology.ecommerce.membership.repository.B2bMembershipRepository;
import com.buyology.ecommerce.membership.repository.B2bSetupTokenRepository;
import com.buyology.ecommerce.membership.repository.WalletRepository;
import com.buyology.ecommerce.user.domain.Users;
import com.buyology.ecommerce.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
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
    private final B2bCountryRepository countryRepo;
    private final B2bSetupTokenRepository setupTokenRepo;
    private final UserRepository userRepository;
    private final AuthCredentialRepository authCredentialRepository;

    @Value("${app.web-base-url:https://buyology.online}")
    private String webBaseUrl;

    public B2bMembershipService(B2bMembershipApplicationRepository appRepo,
                                 B2bMembershipRepository membershipRepo,
                                 WalletRepository walletRepo,
                                 WalletService walletService,
                                 EmailService emailService,
                                 B2bCountryRepository countryRepo,
                                 B2bSetupTokenRepository setupTokenRepo,
                                 UserRepository userRepository,
                                 AuthCredentialRepository authCredentialRepository) {
        this.appRepo = appRepo;
        this.membershipRepo = membershipRepo;
        this.walletRepo = walletRepo;
        this.walletService = walletService;
        this.emailService = emailService;
        this.countryRepo = countryRepo;
        this.setupTokenRepo = setupTokenRepo;
        this.userRepository = userRepository;
        this.authCredentialRepository = authCredentialRepository;
    }

    // ── Customer endpoints ───────────────────────────────────────────────────

    @Transactional
    public MembershipApplicationResponse submitApplication(MembershipApplicationRequest req, UUID userId) {
        String email = req.getContactEmail() == null ? null : req.getContactEmail().trim().toLowerCase();

        if (userId != null && membershipRepo.existsByUserId(userId)) {
            throw new IllegalStateException("This account already has an active B2B membership");
        }
        if (userId != null) {
            Optional<B2bMembershipApplication> existing = appRepo.findByUserId(userId);
            if (existing.isPresent()
                    && existing.get().getStatus() != B2bMembershipApplication.ApplicationStatus.REJECTED) {
                throw new IllegalStateException(
                        "A B2B membership application already exists for this account (status: "
                                + existing.get().getStatus() + ")");
            }
        }
        if (email != null && !email.isBlank()
                && appRepo.existsByContactEmailAndStatusNot(email, B2bMembershipApplication.ApplicationStatus.REJECTED)) {
            throw new IllegalStateException("A B2B membership application already exists for this email");
        }

        B2bMembershipApplication app = new B2bMembershipApplication();
        app.setUserId(userId);
        app.setCompanyName(req.getCompanyName());
        app.setTradeLicenseNumber(req.getTradeLicenseNumber());
        app.setIndustryType(req.getIndustryType());
        app.setNumberOfEmployees(req.getNumberOfEmployees());
        app.setCountry(req.getCountry());

        // Resolve B2B country / currency by ISO code (admin-managed via /admin/b2b/countries)
        if (req.getCountryCode() != null && !req.getCountryCode().isBlank()) {
            String cc = req.getCountryCode().trim().toUpperCase();
            B2bCountry country = countryRepo.findByCountryCode(cc)
                    .orElseThrow(() -> new IllegalStateException(
                            "B2B membership is not available in this country yet"));
            if (!country.isEnabled()) {
                throw new IllegalStateException("B2B membership is not available in this country yet");
            }
            app.setCountryCode(country.getCountryCode());
            app.setCurrencyCode(country.getCurrencyCode());
        }
        app.setCity(req.getCity());
        app.setWebsite(req.getWebsite());
        app.setContactFullName(req.getContactFullName());
        app.setContactDesignation(req.getContactDesignation());
        app.setContactEmail(email);
        app.setContactMobile(req.getContactMobile());
        app.setTermsAccepted(req.isTermsAccepted());
        if (req.getBusinessNeeds() != null) {
            app.setBusinessNeeds(String.join(",", req.getBusinessNeeds()));
        }
        app = appRepo.save(app);

        // Internal notification to ops
        try {
            emailService.sendB2bInquiryNotification(
                    "firdovsirz@gmail.com",
                    app.getCompanyName(), app.getContactFullName(),
                    app.getContactEmail(), app.getContactMobile(),
                    0, "B2B Membership Application submitted - Status: PENDING");
        } catch (Exception e) {
            log.warn("Admin notification failed: {}", e.getMessage());
        }

        // Confirmation email to the applicant — "received, under review (1–5 business days)"
        try {
            emailService.sendB2bApplicationReceivedEmail(
                    app.getContactEmail(),
                    app.getContactFullName(),
                    app.getCompanyName());
        } catch (Exception e) {
            log.warn("Applicant confirmation email failed for {}: {}", app.getContactEmail(), e.getMessage());
        }

        return toAppResponse(app);
    }

    public MembershipApplicationResponse getMyApplication(UUID userOrAuthCredId) {
        UUID resolved = resolveUsersId(userOrAuthCredId);
        B2bMembershipApplication app = appRepo.findByUserId(resolved)
                .orElseThrow(() -> new NoSuchElementException("No application found for user"));
        return toAppResponse(app);
    }

    public MembershipCardResponse getMembershipCard(UUID userOrAuthCredId) {
        UUID resolved = resolveUsersId(userOrAuthCredId);
        B2bMembership membership = membershipRepo.findByUserId(resolved)
                .orElseThrow(() -> new NoSuchElementException("No active membership found"));

        Optional<Wallet> wallet = walletRepo.findByUserId(resolved);
        return toCardResponse(membership, wallet.orElse(null));
    }

    /**
     * Membership endpoints historically accepted users.id, but the frontend now sends
     * either users.id (uid) or auth_credentials.id (sub) depending on the call path.
     * Resolve to users.id by trying both lookups so both work transparently.
     */
    public UUID resolveUsersId(UUID candidate) {
        if (candidate == null) return null;
        // Fast path: already a users.id with a membership row.
        if (membershipRepo.existsByUserId(candidate)) return candidate;
        // Fallback: maybe it's an auth_credentials.id — resolve via the credentials row.
        return authCredentialRepository.findById(candidate)
                .map(c -> c.getUserId())
                .orElse(candidate);
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
        // Ensure a User + LOCAL AuthCredentials exists for this applicant so they
        // can sign in via the setup link. Two-pass: first resolve userId, then
        // unconditionally guarantee a LOCAL credential row exists for that user.
        UUID userId = app.getUserId();
        String email = app.getContactEmail();
        if (userId == null) {
            AuthCredentials existingCreds = authCredentialRepository
                    .findByEmailAndProvider(email, "LOCAL")
                    .orElse(null);
            if (existingCreds != null) {
                userId = existingCreds.getUserId();
            } else {
                Users user = new Users();
                String[] nameParts = app.getContactFullName() == null
                        ? new String[]{"B2B", "Member"}
                        : app.getContactFullName().trim().split("\\s+", 2);
                user.setFirstName(nameParts[0]);
                user.setLastName(nameParts.length > 1 ? nameParts[1] : "");
                user.setUserType(Users.UserType.CUSTOMER);
                user.setIsGuest(false);
                user.setStatus("ACTIVE");
                userRepository.save(user);
                userId = user.getId();
            }
            app.setUserId(userId);
            appRepo.save(app);
        }

        // Guarantee a LOCAL credential exists. Covers cases where:
        //  - the applicant was logged in as an OAuth-only user when they applied
        //  - the row was created on an older code path that skipped this step
        AuthCredentials localCreds = authCredentialRepository.findByUserId(userId).stream()
                .filter(c -> "LOCAL".equalsIgnoreCase(c.getProvider()))
                .findFirst()
                .orElse(null);
        if (localCreds == null) {
            localCreds = new AuthCredentials();
            localCreds.setUserId(userId);
            localCreds.setEmail(email);
            localCreds.setProvider("LOCAL");
            localCreds.setIsActive(true);
            localCreds.setPhoneVerified(false);
            authCredentialRepository.save(localCreds);
        } else if ((localCreds.getEmail() == null || localCreds.getEmail().isBlank()) && email != null) {
            // Backfill missing email so /validate-token can show the account.
            localCreds.setEmail(email);
            authCredentialRepository.save(localCreds);
        }

        B2bMembership membership;
        if (membershipRepo.existsByUserId(userId)) {
            membership = membershipRepo.findByUserId(userId).orElseThrow();
        } else {
            membership = new B2bMembership();
            membership.setMembershipId(generateMembershipId());
            membership.setUserId(userId);
            membership.setApplicationId(app.getId());
            membership.setCompanyName(app.getCompanyName());
            membership.setMemberName(app.getContactFullName());
            membership.setStatus(B2bMembership.MembershipStatus.ACTIVE);
            membership.setTier(B2bMembership.MembershipTier.PREMIUM);
            membership = membershipRepo.save(membership);
        }

        WalletResponse wallet = walletService.addInitialCredit(
                userId,
                performedBy,
                app.getCurrencyCode() != null ? app.getCurrencyCode() : "AED",
                app.getCountryCode());

        // Invalidate any prior unused setup tokens for this membership so the
        // approval email always contains the only valid link (re-approval flow).
        var oldTokens = setupTokenRepo.findByMembershipIdAndUsedFalse(membership.getId());
        for (B2bSetupToken old : oldTokens) {
            old.setUsed(true);
            setupTokenRepo.save(old);
        }

        // Setup token (24h TTL) — included in the approval email so the new
        // member can set a password and sign in.
        B2bSetupToken token = new B2bSetupToken();
        token.setMembershipId(membership.getId());
        token.setToken(UUID.randomUUID().toString());
        token.setExpiresAt(Instant.now().plus(24, java.time.temporal.ChronoUnit.HOURS));
        setupTokenRepo.save(token);
        String setupLink = webBaseUrl + "/b2b/set-password?token=" + token.getToken();

        try {
            emailService.sendB2bMembershipApprovedEmail(
                    app.getContactEmail(),
                    app.getContactFullName(),
                    app.getCompanyName(),
                    membership.getMembershipId(),
                    wallet.getCreditLimit() != null
                            ? wallet.getCreditLimit().toPlainString()
                            : "5000",
                    wallet.getCurrency(),
                    setupLink);
        } catch (Exception e) {
            log.warn("Approval email failed: {}", e.getMessage());
        }
    }

    private void sendRejectionEmail(B2bMembershipApplication app) {
        try {
            emailService.sendB2bMembershipRejectedEmail(
                    app.getContactEmail(),
                    app.getContactFullName(),
                    app.getCompanyName(),
                    app.getRejectionReason());
        } catch (Exception e) {
            log.warn("Rejection email failed: {}", e.getMessage());
        }
    }

    private String generateMembershipId() {
        // DB-aware to survive server restarts. Tries the in-memory sequence first
        // (fast path) then bumps until an unused value is found.
        int year = Year.now().getValue();
        for (int attempts = 0; attempts < 1000; attempts++) {
            String candidate = "BUY-" + year + "-" + SEQ.getAndIncrement();
            if (membershipRepo.findByMembershipId(candidate).isEmpty()) {
                return candidate;
            }
        }
        // Fallback — extremely unlikely. Use a UUID suffix to guarantee uniqueness.
        return "BUY-" + year + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
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
        r.setPasswordSet(hasLocalPassword(m.getUserId()));
        if (wallet != null) {
            r.setWalletBalance(wallet.getBalance());
            r.setWalletCurrency(wallet.getCurrency());
        }
        return r;
    }

    private boolean hasLocalPassword(UUID userId) {
        if (userId == null) return false;
        return authCredentialRepository.findByUserId(userId).stream()
                .filter(c -> "LOCAL".equalsIgnoreCase(c.getProvider()))
                .anyMatch(c -> c.getPasswordHash() != null && !c.getPasswordHash().isBlank());
    }

    /**
     * Re-issue the password-setup email for a member who hasn't completed
     * setup yet. Invalidates any prior unused tokens, mints a fresh one,
     * and re-sends the approval email. Refuses if the member already has a
     * LOCAL password.
     */
    @Transactional
    public void resendSetupEmail(UUID membershipId) {
        B2bMembership membership = membershipRepo.findById(membershipId)
                .orElseThrow(() -> new NoSuchElementException("Membership not found"));
        if (membership.getStatus() != B2bMembership.MembershipStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Membership is not active (status: " + membership.getStatus() + ")");
        }
        if (hasLocalPassword(membership.getUserId())) {
            throw new IllegalStateException("Member has already set a password");
        }

        B2bMembershipApplication app = membership.getApplicationId() == null
                ? null
                : appRepo.findById(membership.getApplicationId()).orElse(null);

        String email = app != null ? app.getContactEmail() : null;
        if (email == null) {
            email = authCredentialRepository.findByUserId(membership.getUserId()).stream()
                    .filter(c -> "LOCAL".equalsIgnoreCase(c.getProvider()))
                    .map(AuthCredentials::getEmail)
                    .filter(e -> e != null && !e.isBlank())
                    .findFirst()
                    .orElse(null);
        }
        if (email == null || email.isBlank()) {
            throw new IllegalStateException("No contact email on file for this member");
        }

        // Invalidate any prior unused setup tokens so only the new one is valid.
        var oldTokens = setupTokenRepo.findByMembershipIdAndUsedFalse(membership.getId());
        for (B2bSetupToken old : oldTokens) {
            old.setUsed(true);
            setupTokenRepo.save(old);
        }

        B2bSetupToken token = new B2bSetupToken();
        token.setMembershipId(membership.getId());
        token.setToken(UUID.randomUUID().toString());
        token.setExpiresAt(Instant.now().plus(24, java.time.temporal.ChronoUnit.HOURS));
        setupTokenRepo.save(token);

        Wallet wallet = walletRepo.findByUserId(membership.getUserId()).orElse(null);
        String creditLimit = wallet != null && wallet.getCreditLimit() != null
                ? wallet.getCreditLimit().toPlainString()
                : "5000";
        String currency = wallet != null && wallet.getCurrency() != null
                ? wallet.getCurrency()
                : (app != null && app.getCurrencyCode() != null ? app.getCurrencyCode() : "AED");

        String setupLink = webBaseUrl + "/b2b/set-password?token=" + token.getToken();
        try {
            emailService.sendB2bMembershipApprovedEmail(
                    email,
                    membership.getMemberName() != null
                            ? membership.getMemberName()
                            : (app != null ? app.getContactFullName() : "Member"),
                    membership.getCompanyName(),
                    membership.getMembershipId(),
                    creditLimit,
                    currency,
                    setupLink);
        } catch (Exception e) {
            log.warn("Resend setup email failed for membershipId={}: {}", membershipId, e.getMessage());
            throw new IllegalStateException("Failed to send email: " + e.getMessage());
        }
    }
}
