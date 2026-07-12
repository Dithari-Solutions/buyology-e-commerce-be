package com.buyology.ecommerce.membership.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.common.utils.SecurityUtils;
import com.buyology.ecommerce.membership.dto.*;
import com.buyology.ecommerce.membership.service.B2bMembershipLifecycleService;
import com.buyology.ecommerce.membership.service.B2bMembershipService;
import com.buyology.ecommerce.membership.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/membership")
public class B2bMembershipController {

    private final B2bMembershipService membershipService;
    private final WalletService walletService;
    private final B2bMembershipLifecycleService lifecycleService;

    public B2bMembershipController(B2bMembershipService membershipService,
                                   WalletService walletService,
                                   B2bMembershipLifecycleService lifecycleService) {
        this.membershipService = membershipService;
        this.walletService = walletService;
        this.lifecycleService = lifecycleService;
    }

    /**
     * Public B2B membership application, submitted from the sign-up flow (no login).
     * The applicant sets a password and uploads a trade-license document (required);
     * the account is created and activated only once an admin approves. The form is
     * sent as multipart/form-data: an "application" JSON part + a "tradeLicense" file.
     */
    @PostMapping(value = "/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<MembershipApplicationResponse>> apply(
            @Valid @RequestPart("application") MembershipApplicationRequest req,
            @RequestPart("tradeLicense") MultipartFile tradeLicense) {
        return ApiResponse.created(membershipService.submitApplication(req, tradeLicense),
                "Application submitted successfully. We'll review it shortly.");
    }

    /**
     * Self-service B2B application for an already-signed-in customer (upgrade from
     * their existing account). Unlike {@link #apply}, no password is captured — the
     * user keeps their current login — and the application is pre-attached to the
     * authenticated user. Created as PENDING and reviewed via the normal admin queue.
     * Sent as multipart/form-data: a "data" JSON part + a "tradeLicense" file part.
     */
    @PostMapping(value = "/apply-self", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<MembershipApplicationResponse>> applySelf(
            @Valid @RequestPart("data") B2bConversionRequest req,
            @RequestPart("tradeLicense") MultipartFile tradeLicense) {
        UUID userId = SecurityUtils.currentUserId();
        return ApiResponse.created(
                membershipService.convertUserToB2b(userId, req, tradeLicense),
                "Application submitted successfully. We'll review it shortly.");
    }

    @GetMapping("/application")
    public ResponseEntity<ApiResponse<MembershipApplicationResponse>> getMyApplication(
            @RequestParam UUID userId) {
        SecurityUtils.requireSelf(membershipService.resolveUsersId(userId));
        return ApiResponse.success(membershipService.getMyApplication(userId), "Application fetched");
    }

    @GetMapping("/card")
    public ResponseEntity<ApiResponse<MembershipCardResponse>> getMembershipCard(
            @RequestParam UUID userId) {
        SecurityUtils.requireSelf(membershipService.resolveUsersId(userId));
        return ApiResponse.success(membershipService.getMembershipCard(userId), "Membership card fetched");
    }

    @GetMapping("/wallet")
    public ResponseEntity<ApiResponse<WalletResponse>> getWallet(
            @RequestParam UUID userId,
            @RequestParam(required = false) String currency) {
        UUID resolved = membershipService.resolveUsersId(userId);
        SecurityUtils.requireSelf(resolved);
        return ApiResponse.success(
                walletService.getWallet(resolved, currency),
                "Wallet fetched");
    }

    @GetMapping("/wallet/transactions")
    public ResponseEntity<ApiResponse<List<WalletTransactionResponse>>> getTransactions(
            @RequestParam UUID userId) {
        UUID resolved = membershipService.resolveUsersId(userId);
        SecurityUtils.requireSelf(resolved);
        return ApiResponse.success(
                walletService.getTransactions(resolved),
                "Transactions fetched");
    }

    // ── Self lifecycle ─────────────────────────────────────────────────────

    @PostMapping("/freeze")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<String>> freezeMyMembership() {
        return lifecycleService.selfFreeze();
    }

    @PostMapping("/unfreeze")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<String>> unfreezeMyMembership() {
        return lifecycleService.selfUnfreeze();
    }

    @DeleteMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<String>> deleteMyMembership() {
        return lifecycleService.selfDelete();
    }
}
