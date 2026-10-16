package com.buyology.ecommerce.privacy.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.privacy.dto.ConsentRequest;
import com.buyology.ecommerce.privacy.service.CookieConsentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class CookieConsentController {
    private final CookieConsentService service;
    public CookieConsentController(CookieConsentService service) { this.service = service; }
    public record Receipt(java.util.UUID decisionId, boolean recorded) {}

    @PostMapping("/api/privacy/consents")
    public ResponseEntity<ApiResponse<Receipt>> record(@Valid @RequestBody ConsentRequest request,
            @RequestHeader(value = "Origin", required = false) String origin) {
        var saved = service.record(request, origin);
        return ApiResponse.success(new Receipt(saved.decisionId(), true), "Cookie choice recorded");
    }

    @GetMapping("/api/privacy/cookies")
    public ResponseEntity<ApiResponse<CookieConsentService.Policy>> policy() {
        return ApiResponse.success(service.policy(), "Cookie information");
    }

    @GetMapping("/api/admin/privacy/consents")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<ApiResponse<CookieConsentService.DecisionPage>> list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size,
            @RequestParam(required = false) String consentId, @RequestParam(required = false) Boolean analytics,
            @RequestParam(required = false) Boolean marketing) {
        return ApiResponse.success(service.list(page, size, consentId, analytics, marketing), "Cookie consent decisions");
    }
}
