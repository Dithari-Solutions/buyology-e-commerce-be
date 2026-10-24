package com.buyology.ecommerce.warranty.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.warranty.dto.WarrantyPresetRequest;
import com.buyology.ecommerce.warranty.dto.WarrantyPresetResponse;
import com.buyology.ecommerce.warranty.service.WarrantyPresetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/warranty-presets")
@Tag(name = "Admin Warranty Presets", description = "Named warranty lengths offered in the product form")
public class AdminWarrantyPresetController {

    private final WarrantyPresetService warrantyPresetService;

    public AdminWarrantyPresetController(WarrantyPresetService warrantyPresetService) {
        this.warrantyPresetService = warrantyPresetService;
    }

    // Readable by whoever can create or edit products, not only by preset managers: the product
    // form's dropdown is filled from this list.
    @Operation(summary = "List warranty presets, shortest first, with how many products use each")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('product:warranty:read') or hasAuthority('product:create') "
            + "or hasAuthority('product:update') or @rbacPolicy.legacyAdmin()")
    @GetMapping
    public ResponseEntity<ApiResponse<List<WarrantyPresetResponse>>> list() {
        return ApiResponse.success(warrantyPresetService.list(), "Warranty presets fetched successfully");
    }

    @Operation(summary = "Create a warranty preset")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('product:warranty:create') or @rbacPolicy.legacyAdmin()")
    @PostMapping
    public ResponseEntity<ApiResponse<WarrantyPresetResponse>> create(@RequestBody WarrantyPresetRequest request) {
        return ApiResponse.created(warrantyPresetService.create(request), "Warranty preset created successfully");
    }

    @Operation(summary = "Rename a preset or change its length",
            description = "Products using the preset take the new length. Cards already issued keep theirs.")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('product:warranty:update') or @rbacPolicy.legacyAdmin()")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<WarrantyPresetResponse>> update(@PathVariable UUID id,
                                                                       @RequestBody WarrantyPresetRequest request) {
        return ApiResponse.success(warrantyPresetService.update(id, request), "Warranty preset updated successfully");
    }

    @Operation(summary = "Delete a warranty preset",
            description = "Products using it keep their length as a custom duration. Returns how many were switched.")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('product:warranty:delete') or @rbacPolicy.legacyAdmin()")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Integer>> delete(@PathVariable UUID id) {
        return ApiResponse.success(warrantyPresetService.delete(id), "Warranty preset deleted successfully");
    }
}
