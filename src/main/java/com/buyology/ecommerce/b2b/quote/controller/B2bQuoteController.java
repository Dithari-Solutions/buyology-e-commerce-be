package com.buyology.ecommerce.b2b.quote.controller;

import com.buyology.ecommerce.b2b.quote.dto.*;
import com.buyology.ecommerce.b2b.quote.service.B2bQuoteService;
import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.payment.dto.PaymentInitiatedResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Member-facing B2B RFQ endpoints. All require authentication; the service enforces that the
 * caller holds an ACTIVE B2bMembership and owns the quote (keyed on their credential / sub).
 */
@RestController
@RequestMapping("/api/b2b/quote")
@PreAuthorize("isAuthenticated()")
public class B2bQuoteController {

    private final B2bQuoteService quoteService;

    public B2bQuoteController(B2bQuoteService quoteService) {
        this.quoteService = quoteService;
    }

    // ── B2B cart (DRAFT quote) ─────────────────────────────────────────────────

    @GetMapping("/cart")
    public ResponseEntity<ApiResponse<B2bQuoteResponse>> getCart(
            @AuthenticationPrincipal UUID userId) {
        return ApiResponse.success(quoteService.getOrCreateCart(userId), "B2B cart fetched");
    }

    @PostMapping("/cart/items")
    public ResponseEntity<ApiResponse<B2bQuoteResponse>> addItem(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody AddQuoteItemRequest req) {
        return ApiResponse.success(quoteService.addItem(userId, req), "Item added to B2B cart");
    }

    @PatchMapping("/cart/items/{itemId}")
    public ResponseEntity<ApiResponse<B2bQuoteResponse>> updateItem(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateQuoteItemRequest req) {
        return ApiResponse.success(quoteService.updateItem(userId, itemId, req), "Line updated");
    }

    @DeleteMapping("/cart/items/{itemId}")
    public ResponseEntity<ApiResponse<B2bQuoteResponse>> removeItem(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID itemId) {
        return ApiResponse.success(quoteService.removeItem(userId, itemId), "Line removed");
    }

    @PostMapping("/cart/submit")
    public ResponseEntity<ApiResponse<B2bQuoteResponse>> submit(
            @AuthenticationPrincipal UUID userId,
            @RequestBody(required = false) @Valid SubmitQuoteRequest req) {
        return ApiResponse.success(quoteService.submit(userId, req), "Quote request submitted");
    }

    // ── Quote history / lifecycle ──────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<ApiResponse<List<B2bQuoteResponse>>> listMyQuotes(
            @AuthenticationPrincipal UUID userId) {
        return ApiResponse.success(quoteService.listMyQuotes(userId), "Quotes fetched");
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<B2bQuoteResponse>> getMyQuote(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        return ApiResponse.success(quoteService.getMyQuote(userId, id), "Quote fetched");
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<ApiResponse<B2bQuoteResponse>> accept(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        return ApiResponse.success(quoteService.accept(userId, id), "Quote accepted");
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<B2bQuoteResponse>> cancel(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        return ApiResponse.success(quoteService.cancel(userId, id), "Quote cancelled");
    }

    @PostMapping("/{id}/checkout")
    public ResponseEntity<ApiResponse<PaymentInitiatedResponse>> checkout(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id,
            @Valid @RequestBody CheckoutQuoteRequest req) {
        return ApiResponse.created(quoteService.checkout(userId, id, req), "Checkout initiated");
    }
}
