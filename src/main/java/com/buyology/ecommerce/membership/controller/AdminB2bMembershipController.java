package com.buyology.ecommerce.membership.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.membership.dto.*;
import com.buyology.ecommerce.membership.service.B2bMembershipLifecycleService;
import com.buyology.ecommerce.membership.service.B2bMembershipService;
import com.buyology.ecommerce.membership.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/membership")
@PreAuthorize("hasRole('ADMIN')")
public class AdminB2bMembershipController {

    private final B2bMembershipService membershipService;
    private final WalletService walletService;
    private final B2bMembershipLifecycleService lifecycleService;

    public AdminB2bMembershipController(B2bMembershipService membershipService,
                                        WalletService walletService,
                                        B2bMembershipLifecycleService lifecycleService) {
        this.membershipService = membershipService;
        this.walletService = walletService;
        this.lifecycleService = lifecycleService;
    }

    // ── Applications ──────────────────────────────────────────────────────────

    @GetMapping("/applications")
    public ResponseEntity<ApiResponse<List<MembershipApplicationResponse>>> listApplications() {
        return ApiResponse.success(membershipService.listAllApplications(), "Applications fetched");
    }

    @PostMapping("/applications/{id}/action")
    public ResponseEntity<ApiResponse<MembershipApplicationResponse>> processAction(
            @PathVariable UUID id,
            @Valid @RequestBody ApplicationActionRequest req) {
        return ApiResponse.success(membershipService.processAction(id, req), "Action applied");
    }

    // ── Memberships ───────────────────────────────────────────────────────────

    @GetMapping("/memberships")
    public ResponseEntity<ApiResponse<List<MembershipCardResponse>>> listMemberships() {
        return ApiResponse.success(membershipService.listAllMemberships(), "Memberships fetched");
    }

    // ── Wallet Management ─────────────────────────────────────────────────────

    @GetMapping("/wallet/{userId}")
    public ResponseEntity<ApiResponse<WalletResponse>> getUserWallet(@PathVariable UUID userId) {
        return ApiResponse.success(walletService.getWallet(userId), "Wallet fetched");
    }

    @PostMapping("/wallet/{userId}/credit")
    public ResponseEntity<ApiResponse<WalletResponse>> addCredit(
            @PathVariable UUID userId,
            @Valid @RequestBody WalletCreditRequest req) {
        return ApiResponse.success(
                walletService.addCredit(userId, req.getAmount(), req.getDescription(), req.getPerformedBy()),
                "Credit added");
    }

    @PostMapping("/wallet/{userId}/deduct")
    public ResponseEntity<ApiResponse<WalletResponse>> deductCredit(
            @PathVariable UUID userId,
            @Valid @RequestBody WalletCreditRequest req) {
        return ApiResponse.success(
                walletService.deductCredit(userId, req.getAmount(), req.getDescription(), null),
                "Credit deducted");
    }

    @PostMapping("/wallet/{userId}/adjust")
    public ResponseEntity<ApiResponse<WalletResponse>> adjustBalance(
            @PathVariable UUID userId,
            @Valid @RequestBody WalletCreditRequest req) {
        return ApiResponse.success(
                walletService.adjustment(userId, req.getAmount(), req.getDescription(), req.getPerformedBy()),
                "Balance adjusted");
    }

    @GetMapping("/wallet/{userId}/transactions")
    public ResponseEntity<ApiResponse<List<WalletTransactionResponse>>> getTransactions(
            @PathVariable UUID userId) {
        return ApiResponse.success(walletService.getTransactions(userId), "Transactions fetched");
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    @PostMapping("/memberships/{id}/freeze")
    public ResponseEntity<ApiResponse<String>> freeze(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID actorId) {
        return lifecycleService.adminFreeze(id, actorId != null ? "admin:" + actorId : "admin");
    }

    @PostMapping("/memberships/{id}/unfreeze")
    public ResponseEntity<ApiResponse<String>> unfreeze(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID actorId) {
        return lifecycleService.adminUnfreeze(id, actorId != null ? "admin:" + actorId : "admin");
    }

    @DeleteMapping("/memberships/{id}")
    public ResponseEntity<ApiResponse<String>> trash(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID actorId) {
        return lifecycleService.adminDelete(id, actorId != null ? "admin:" + actorId : "admin");
    }

    @PostMapping("/memberships/{id}/restore")
    public ResponseEntity<ApiResponse<String>> restore(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID actorId) {
        return lifecycleService.adminRestore(id, actorId != null ? "admin:" + actorId : "admin");
    }

    /**
     * Re-send the set-password email to a member who hasn't completed setup.
     * Errors with 409 if the member already has a password.
     */
    @PostMapping("/memberships/{id}/resend-setup")
    public ResponseEntity<ApiResponse<String>> resendSetup(@PathVariable UUID id) {
        try {
            membershipService.resendSetupEmail(id);
            return ApiResponse.success("sent", "Set-password email re-sent");
        } catch (IllegalStateException e) {
            return ApiResponse.failure(org.springframework.http.HttpStatus.CONFLICT, e.getMessage());
        } catch (java.util.NoSuchElementException e) {
            return ApiResponse.failure(org.springframework.http.HttpStatus.NOT_FOUND, e.getMessage());
        }
    }
}
