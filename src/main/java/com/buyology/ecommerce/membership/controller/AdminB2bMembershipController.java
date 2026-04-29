package com.buyology.ecommerce.membership.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.membership.dto.*;
import com.buyology.ecommerce.membership.service.B2bMembershipService;
import com.buyology.ecommerce.membership.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/membership")
@PreAuthorize("hasRole('ADMIN')")
public class AdminB2bMembershipController {

    private final B2bMembershipService membershipService;
    private final WalletService walletService;

    public AdminB2bMembershipController(B2bMembershipService membershipService, WalletService walletService) {
        this.membershipService = membershipService;
        this.walletService = walletService;
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
}
