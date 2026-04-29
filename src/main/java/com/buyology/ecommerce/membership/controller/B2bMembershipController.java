package com.buyology.ecommerce.membership.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.membership.dto.*;
import com.buyology.ecommerce.membership.service.B2bMembershipService;
import com.buyology.ecommerce.membership.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/membership")
public class B2bMembershipController {

    private final B2bMembershipService membershipService;
    private final WalletService walletService;

    public B2bMembershipController(B2bMembershipService membershipService, WalletService walletService) {
        this.membershipService = membershipService;
        this.walletService = walletService;
    }

    @PostMapping("/apply")
    public ResponseEntity<ApiResponse<MembershipApplicationResponse>> apply(
            @Valid @RequestBody MembershipApplicationRequest req,
            @RequestParam(required = false) UUID userId) {
        return ApiResponse.created(membershipService.submitApplication(req, userId),
                "Application submitted successfully. We'll review it shortly.");
    }

    @GetMapping("/application")
    public ResponseEntity<ApiResponse<MembershipApplicationResponse>> getMyApplication(
            @RequestParam UUID userId) {
        return ApiResponse.success(membershipService.getMyApplication(userId), "Application fetched");
    }

    @GetMapping("/card")
    public ResponseEntity<ApiResponse<MembershipCardResponse>> getMembershipCard(
            @RequestParam UUID userId) {
        return ApiResponse.success(membershipService.getMembershipCard(userId), "Membership card fetched");
    }

    @GetMapping("/wallet")
    public ResponseEntity<ApiResponse<WalletResponse>> getWallet(
            @RequestParam UUID userId) {
        return ApiResponse.success(walletService.getWallet(userId), "Wallet fetched");
    }

    @GetMapping("/wallet/transactions")
    public ResponseEntity<ApiResponse<List<WalletTransactionResponse>>> getTransactions(
            @RequestParam UUID userId) {
        return ApiResponse.success(walletService.getTransactions(userId), "Transactions fetched");
    }
}
