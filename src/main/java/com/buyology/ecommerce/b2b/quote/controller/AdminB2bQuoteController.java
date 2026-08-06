package com.buyology.ecommerce.b2b.quote.controller;

import com.buyology.ecommerce.b2b.quote.domain.B2bQuoteStatus;
import com.buyology.ecommerce.b2b.quote.dto.B2bQuoteResponse;
import com.buyology.ecommerce.b2b.quote.dto.PriceQuoteRequest;
import com.buyology.ecommerce.b2b.quote.dto.RejectQuoteRequest;
import com.buyology.ecommerce.b2b.quote.service.B2bQuoteService;
import com.buyology.ecommerce.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Procurement-facing B2B quote endpoints. Secured to the SUPERADMIN + PROCUREMENT roles
 * (PROCUREMENT is seeded by the Foundation's RoleDataInitializer).
 */
@RestController
@RequestMapping("/api/admin/b2b/quotes")
public class AdminB2bQuoteController {

    private final B2bQuoteService quoteService;

    public AdminB2bQuoteController(B2bQuoteService quoteService) {
        this.quoteService = quoteService;
    }

    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('b2b:quote:read') or @rbacPolicy.legacyAdmin()")
    @GetMapping
    public ResponseEntity<ApiResponse<List<B2bQuoteResponse>>> list(
            @RequestParam(required = false) B2bQuoteStatus status) {
        return ApiResponse.success(quoteService.listForProcurement(status), "Quotes fetched");
    }

    /**
     * Sidebar badge — number of quotes awaiting a price (SUBMITTED). Declared as the literal
     * {@code /count} path (which Spring matches ahead of {@code /{id}}) so "count" is never
     * captured as a quote id path variable.
     */
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('b2b:quote:read') or @rbacPolicy.legacyAdmin()")
    @GetMapping("/count")
    public ResponseEntity<ApiResponse<NewCountResponse>> newCount() {
        return ApiResponse.success(new NewCountResponse(quoteService.countSubmitted()), "New quote count fetched");
    }

    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('b2b:quote:read') or @rbacPolicy.legacyAdmin()")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<B2bQuoteResponse>> get(@PathVariable UUID id) {
        return ApiResponse.success(quoteService.getForProcurement(id), "Quote fetched");
    }

    /** Shared contract: {@code GET /api/admin/b2b/quotes/count → ApiResponse<{ newCount }>}. */
    public record NewCountResponse(long newCount) {}

    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('b2b:quote:moderate') or @rbacPolicy.legacyAdmin()")
    @PostMapping("/{id}/price")
    public ResponseEntity<ApiResponse<B2bQuoteResponse>> price(
            @AuthenticationPrincipal UUID adminId,
            @PathVariable UUID id,
            @Valid @RequestBody PriceQuoteRequest req) {
        return ApiResponse.success(quoteService.price(adminId, id, req), "Quote priced");
    }

    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('b2b:quote:moderate') or @rbacPolicy.legacyAdmin()")
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<B2bQuoteResponse>> reject(
            @PathVariable UUID id,
            @Valid @RequestBody RejectQuoteRequest req) {
        return ApiResponse.success(quoteService.reject(id, req), "Quote rejected");
    }

    /** Validate an uploaded bank-transfer proof — places the order (AWAITING_PAYMENT_VERIFICATION → ORDERED). */
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('b2b:quote:payment:verify') or @rbacPolicy.legacyAdmin()")
    @PostMapping("/{id}/verify-payment")
    public ResponseEntity<ApiResponse<B2bQuoteResponse>> verifyPayment(
            @AuthenticationPrincipal UUID adminId,
            @PathVariable UUID id) {
        return ApiResponse.success(quoteService.verifyBankTransfer(adminId, id), "Payment verified — order placed");
    }

    /** Reject an uploaded bank-transfer proof — returns the quote to ACCEPTED for re-submission. */
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('b2b:quote:payment:verify') or @rbacPolicy.legacyAdmin()")
    @PostMapping("/{id}/reject-payment")
    public ResponseEntity<ApiResponse<B2bQuoteResponse>> rejectPayment(
            @PathVariable UUID id,
            @Valid @RequestBody RejectQuoteRequest req) {
        return ApiResponse.success(quoteService.rejectBankTransfer(id, req), "Payment proof rejected");
    }
}
