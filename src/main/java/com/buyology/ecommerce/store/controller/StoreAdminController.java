package com.buyology.ecommerce.store.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.store.dto.AssignStoreAdminRequest;
import com.buyology.ecommerce.store.dto.StoreAdminResponse;
import com.buyology.ecommerce.store.dto.UpdateStoreAdminRequest;
import com.buyology.ecommerce.store.service.StoreAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/stores")
@Tag(name = "Store Admin", description = "APIs for assigning and managing store administrators")
public class StoreAdminController {

    private final StoreAdminService storeAdminService;

    public StoreAdminController(StoreAdminService storeAdminService) {
        this.storeAdminService = storeAdminService;
    }

    @Operation(summary = "Assign an admin to a store",
            description = "Assigns a user as STORE_OWNER, STORE_MANAGER, or STORE_STAFF for the given store.")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('store:admin:assign') or @rbacPolicy.legacyAdmin()")
    @PostMapping("/{storeId}/admins")
    public ResponseEntity<ApiResponse<StoreAdminResponse>> assignAdmin(
            @PathVariable UUID storeId,
            @RequestBody @Valid AssignStoreAdminRequest request) {
        return storeAdminService.assignAdmin(storeId, request);
    }

    @Operation(summary = "Get all admins of a store")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('store:admin:read') or @rbacPolicy.legacyAdmin()")
    @GetMapping("/{storeId}/admins")
    public ResponseEntity<ApiResponse<List<StoreAdminResponse>>> getAdminsByStore(
            @PathVariable UUID storeId) {
        return storeAdminService.getAdminsByStore(storeId);
    }

    @Operation(summary = "Get active admins of a store")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('store:admin:read') or @rbacPolicy.legacyAdmin()")
    @GetMapping("/{storeId}/admins/active")
    public ResponseEntity<ApiResponse<List<StoreAdminResponse>>> getActiveAdminsByStore(
            @PathVariable UUID storeId) {
        return storeAdminService.getActiveAdminsByStore(storeId);
    }

    @Operation(summary = "Get a specific store admin by their assignment ID")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('store:admin:read') or @rbacPolicy.legacyAdmin()")
    @GetMapping("/admins/{adminId}")
    public ResponseEntity<ApiResponse<StoreAdminResponse>> getAdminById(
            @PathVariable UUID adminId) {
        return storeAdminService.getAdminById(adminId);
    }

    @Operation(summary = "Update a store admin's role or active status")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('store:admin:update') or @rbacPolicy.legacyAdmin()")
    @PatchMapping("/admins/{adminId}")
    public ResponseEntity<ApiResponse<StoreAdminResponse>> updateAdmin(
            @PathVariable UUID adminId,
            @RequestBody @Valid UpdateStoreAdminRequest request) {
        return storeAdminService.updateAdmin(adminId, request);
    }

    @Operation(summary = "Remove (deactivate) a store admin")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('store:admin:remove') or @rbacPolicy.legacyAdmin()")
    @DeleteMapping("/admins/{adminId}")
    public ResponseEntity<ApiResponse<Void>> removeAdmin(@PathVariable UUID adminId) {
        return storeAdminService.removeAdmin(adminId);
    }
}
