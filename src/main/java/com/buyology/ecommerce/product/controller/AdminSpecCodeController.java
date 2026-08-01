package com.buyology.ecommerce.product.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.product.dto.SpecCodeRequest;
import com.buyology.ecommerce.product.dto.SpecCodeResponse;
import com.buyology.ecommerce.product.service.SpecCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/spec-codes")
@Tag(name = "Admin Spec Codes", description = "Manage the editable registry of product spec codes (ram, storage, gpu, ...)")
public class AdminSpecCodeController {

    private final SpecCodeService specCodeService;

    public AdminSpecCodeController(SpecCodeService specCodeService) {
        this.specCodeService = specCodeService;
    }

    @Operation(summary = "List all spec codes")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('product:spec:read') or @rbacPolicy.legacyAdmin()")
    @GetMapping
    public ResponseEntity<ApiResponse<List<SpecCodeResponse>>> list() {
        return specCodeService.list();
    }

    @Operation(summary = "Create a new spec code")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('product:spec:create') or @rbacPolicy.legacyAdmin()")
    @PostMapping
    public ResponseEntity<ApiResponse<SpecCodeResponse>> create(@Valid @RequestBody SpecCodeRequest req) {
        return specCodeService.create(req);
    }

    @Operation(summary = "Update a spec code's labels / filterable flag / order (code is immutable)")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('product:spec:update') or @rbacPolicy.legacyAdmin()")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SpecCodeResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody SpecCodeRequest req) {
        return specCodeService.update(id, req);
    }

    @Operation(summary = "Delete a spec code")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('product:spec:delete') or @rbacPolicy.legacyAdmin()")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        return specCodeService.delete(id);
    }
}
