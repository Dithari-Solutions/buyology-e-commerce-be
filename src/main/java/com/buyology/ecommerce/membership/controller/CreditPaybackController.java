package com.buyology.ecommerce.membership.controller;

import com.buyology.ecommerce.common.config.PlatformConfigService;
import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.membership.domain.CreditUsage;
import com.buyology.ecommerce.membership.repository.CreditUsageRepository;
import com.buyology.ecommerce.membership.service.CreditPaybackService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class CreditPaybackController {

    private final CreditPaybackService paybackService;
    private final CreditUsageRepository usageRepository;
    private final PlatformConfigService platformConfigService;

    public CreditPaybackController(CreditPaybackService paybackService,
                                   CreditUsageRepository usageRepository,
                                   PlatformConfigService platformConfigService) {
        this.paybackService = paybackService;
        this.usageRepository = usageRepository;
        this.platformConfigService = platformConfigService;
    }

    // ── Member endpoints ────────────────────────────────────────────────────

    @GetMapping("/api/membership/credit/usages")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<CreditUsage>>> myUsages(
            @AuthenticationPrincipal UUID userId) {
        return ApiResponse.success(
                usageRepository.findByUserIdOrderByUsedAtDesc(userId),
                "Credit usages");
    }

    public record InitiatePaybackRequest(BigDecimal amount, String email) {}

    @PostMapping("/api/membership/credit/usages/{id}/pay")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> initiatePayback(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id,
            @RequestBody(required = false) InitiatePaybackRequest body) {
        BigDecimal amount = body != null ? body.amount() : null;
        String email = body != null ? body.email() : null;
        return paybackService.initiatePayback(userId, id, amount, email);
    }

    // ── Admin endpoints ─────────────────────────────────────────────────────

    @GetMapping("/api/admin/membership/credit/usages")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<ApiResponse<List<CreditUsage>>> listUsages(
            @RequestParam(required = false) CreditUsage.Status status) {
        List<CreditUsage> all = usageRepository.findAll();
        if (status != null) {
            all = all.stream().filter(u -> u.getStatus() == status).toList();
        }
        return ApiResponse.success(all, "Credit usages");
    }

    public record SetDeadlineRequest(Instant dueAt, Integer extendDays) {}

    @PatchMapping("/api/admin/membership/credit/usages/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<ApiResponse<CreditUsage>> updateUsageDeadline(
            @PathVariable UUID id,
            @RequestBody SetDeadlineRequest req) {
        CreditUsage usage = usageRepository.findById(id).orElse(null);
        if (usage == null) return ApiResponse.failure(HttpStatus.NOT_FOUND, "Usage not found");
        if (req.dueAt() != null) {
            usage.setDueAt(req.dueAt());
        } else if (req.extendDays() != null) {
            usage.setDueAt(usage.getDueAt().plus(req.extendDays(), ChronoUnit.DAYS));
        } else {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Provide dueAt or extendDays");
        }
        if (usage.getStatus() == CreditUsage.Status.OVERDUE
                && usage.getDueAt().isAfter(Instant.now())) {
            usage.setStatus(CreditUsage.Status.OUTSTANDING);
        }
        return ApiResponse.success(usageRepository.save(usage), "Deadline updated");
    }

    public record PaybackConfigRequest(int paybackDays) {}

    @GetMapping("/api/admin/membership/credit/config")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPaybackConfig() {
        return ApiResponse.success(
                Map.of("paybackDays", platformConfigService.getPaybackDays()),
                "Credit payback config");
    }

    @PatchMapping("/api/admin/membership/credit/config")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updatePaybackConfig(
            @AuthenticationPrincipal UUID actorId,
            @RequestBody PaybackConfigRequest req) {
        platformConfigService.setPaybackDays(req.paybackDays(),
                actorId != null ? "admin:" + actorId : "admin");
        return ApiResponse.success(
                Map.of("paybackDays", platformConfigService.getPaybackDays()),
                "Payback config updated");
    }
}
