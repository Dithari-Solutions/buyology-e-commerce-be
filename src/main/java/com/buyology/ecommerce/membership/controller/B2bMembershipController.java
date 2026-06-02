package com.buyology.ecommerce.membership.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.common.utils.SecurityUtils;
import com.buyology.ecommerce.membership.dto.*;
import com.buyology.ecommerce.membership.service.B2bMembershipLifecycleService;
import com.buyology.ecommerce.membership.service.B2bMembershipService;
import com.buyology.ecommerce.membership.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/apply")
    public ResponseEntity<ApiResponse<MembershipApplicationResponse>> apply(
            @Valid @RequestBody MembershipApplicationRequest req,
            @RequestParam(required = false) UUID userId) {
        // Tie the application to the authenticated caller. The principal IS users.id;
        // ignore any client-supplied userId so applications can't be filed for someone else.
        UUID self = SecurityUtils.currentUserId();
        return ApiResponse.created(membershipService.submitApplication(req, self),
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
            @RequestParam UUID userId) {
        UUID resolved = membershipService.resolveUsersId(userId);
        SecurityUtils.requireSelf(resolved);
        return ApiResponse.success(
                walletService.getWallet(resolved),
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
