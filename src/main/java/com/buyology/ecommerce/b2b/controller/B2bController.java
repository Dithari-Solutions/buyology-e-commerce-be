package com.buyology.ecommerce.b2b.controller;

import com.buyology.ecommerce.b2b.dto.B2bInquiryRequest;
import com.buyology.ecommerce.b2b.dto.B2bInquiryResponse;
import com.buyology.ecommerce.b2b.service.B2bInquiryService;
import com.buyology.ecommerce.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class B2bController {

    private final B2bInquiryService service;

    public B2bController(B2bInquiryService service) {
        this.service = service;
    }

    @PostMapping("/api/b2b/inquiries")
    public ResponseEntity<ApiResponse<B2bInquiryResponse>> submit(
            @Valid @RequestBody B2bInquiryRequest req) {
        return ApiResponse.created(service.submit(req), "Inquiry submitted. We'll be in touch soon!");
    }

    @GetMapping("/api/admin/b2b/inquiries")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('b2b:inquiry:read') or @rbacPolicy.legacyAdmin()")
    public ResponseEntity<ApiResponse<List<B2bInquiryResponse>>> listAll() {
        return ApiResponse.success(service.listAll(), "B2B inquiries fetched");
    }

    @PatchMapping("/api/admin/b2b/inquiries/{id}/status")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('b2b:inquiry:update') or @rbacPolicy.legacyAdmin()")
    public ResponseEntity<ApiResponse<B2bInquiryResponse>> updateStatus(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        String status = body.get("status");
        String notes = body.get("adminNotes");
        return ApiResponse.success(service.updateStatus(id, status, notes), "Status updated");
    }
}
