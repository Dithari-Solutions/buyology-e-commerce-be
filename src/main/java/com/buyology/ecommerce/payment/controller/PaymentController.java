package com.buyology.ecommerce.payment.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.payment.dto.*;
import com.buyology.ecommerce.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Initiate a new payment attempt. Runs the full 3-step Paymob flow and
     * returns the payment key token (card) or redirect URL (Tabby/Tamara).
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/initiate")
    public ResponseEntity<ApiResponse<PaymentInitiatedResponse>> initiatePayment(
            @Valid @RequestBody InitiatePaymentRequest request) {
        return ApiResponse.success(paymentService.initiatePayment(request), "Payment initiated successfully");
    }

    /**
     * Get a single transaction by its ID.
     */
    /**
     * Re-initiate payment for an existing order still awaiting payment ("pay again").
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/orders/{appOrderId}/repay")
    public ResponseEntity<ApiResponse<PaymentInitiatedResponse>> repayOrder(
            @PathVariable UUID appOrderId,
            @Valid @RequestBody RepayOrderRequest request) {
        return ApiResponse.success(paymentService.repayOrder(appOrderId, request), "Payment initiated successfully");
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/transactions/{transactionId}")
    public ResponseEntity<ApiResponse<TransactionResponse>> getTransaction(
            @PathVariable UUID transactionId) {
        return ApiResponse.success(paymentService.getTransaction(transactionId), "Transaction fetched successfully");
    }

    /**
     * Get all payment attempts for a given app order ID.
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/orders/{appOrderId}/transactions")
    public ResponseEntity<ApiResponse<List<TransactionResponse>>> getTransactionsByOrder(
            @PathVariable UUID appOrderId) {
        return ApiResponse.success(paymentService.getTransactionsByOrder(appOrderId), "Transactions fetched successfully");
    }

    /**
     * Initiate a refund (full or partial) for a successful transaction.
     * Enforces the partial refund guard before calling Paymob.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    @PostMapping("/refunds")
    public ResponseEntity<ApiResponse<RefundResponse>> initiateRefund(
            @Valid @RequestBody RefundRequest request) {
        return ApiResponse.success(paymentService.initiateRefund(request), "Refund initiated successfully");
    }
}
