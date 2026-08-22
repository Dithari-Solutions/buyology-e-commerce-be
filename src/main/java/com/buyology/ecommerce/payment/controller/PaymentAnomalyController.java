package com.buyology.ecommerce.payment.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.common.utils.SecurityUtils;
import com.buyology.ecommerce.payment.domain.PaymentAnomaly;
import com.buyology.ecommerce.payment.repository.PaymentAnomalyRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The payment-review queue: settled payments that could not be applied to their order.
 *
 * <p>Read the queue, and close a case with a note. There is deliberately no endpoint that moves
 * money — a human who decides to refund uses the existing refund flow, whose guards (claim-first,
 * PENDING counts as spent) already make a double-send impossible; this controller only records the
 * decision.
 */
@Tag(name = "Admin — Payment review")
@RestController
@RequestMapping("/api/admin/payment-anomalies")
public class PaymentAnomalyController {

    private final PaymentAnomalyRepository anomalyRepo;

    public PaymentAnomalyController(PaymentAnomalyRepository anomalyRepo) {
        this.anomalyRepo = anomalyRepo;
    }

    @Operation(summary = "List payment anomalies, open ones first (resolution=OPEN by default; ALL for everything)")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('payment:refund') or @rbacPolicy.legacyAdmin()")
    @GetMapping
    public ResponseEntity<ApiResponse<List<PaymentAnomaly>>> list(
            @RequestParam(defaultValue = "OPEN") String resolution,
            @RequestParam(defaultValue = "100") int limit) {
        int size = Math.min(Math.max(limit, 1), 500);
        List<PaymentAnomaly> rows = "ALL".equalsIgnoreCase(resolution)
                ? anomalyRepo.findAllByOrderByCreatedAtDesc(PageRequest.of(0, size))
                : anomalyRepo.findByResolutionOrderByCreatedAtAsc(
                        resolution.toUpperCase(java.util.Locale.ROOT), PageRequest.of(0, size));
        return ApiResponse.success(rows, "Payment anomalies");
    }

    @Operation(summary = "Close a payment anomaly with a note — records the decision, never moves money")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('payment:refund') or @rbacPolicy.legacyAdmin()")
    @PostMapping("/{id}/resolve")
    @Transactional
    public ResponseEntity<ApiResponse<PaymentAnomaly>> resolve(
            @PathVariable UUID id, @RequestBody Map<String, String> body) {
        String note = body == null ? null : body.get("note");
        if (note == null || note.isBlank()) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST,
                    "A resolution note is required — say what was decided and why.");
        }
        PaymentAnomaly anomaly = anomalyRepo.findById(id).orElse(null);
        if (anomaly == null) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Payment anomaly not found");
        }
        if ("AUTO_REFUNDING".equals(anomaly.getResolution())) {
            return ApiResponse.failure(HttpStatus.CONFLICT,
                    "This anomaly is mid-refund; wait for the sweep to finish before closing it.");
        }
        anomaly.setResolution("RESOLVED");
        anomaly.setResolutionNote(note);
        anomaly.setResolvedBy(SecurityUtils.currentUserIdOrNull());
        anomaly.setResolvedAt(Instant.now());
        return ApiResponse.success(anomalyRepo.save(anomaly), "Resolved");
    }
}
