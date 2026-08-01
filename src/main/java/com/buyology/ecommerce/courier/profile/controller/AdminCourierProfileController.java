package com.buyology.ecommerce.courier.profile.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.courier.profile.dto.CourierProfileResponse;
import com.buyology.ecommerce.courier.profile.dto.CreateCourierProfileRequest;
import com.buyology.ecommerce.courier.profile.dto.UpdateCourierProfileRequest;
import com.buyology.ecommerce.courier.profile.service.CourierProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Admin management of per-store courier profiles. Each store has its own couriers;
 * an order is later assigned one of its store's couriers for delivery.
 */
@RestController
@RequestMapping("/api/admin/courier-profiles")
public class AdminCourierProfileController {

    private final CourierProfileService service;

    public AdminCourierProfileController(CourierProfileService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('courier:profile:read') or @rbacPolicy.legacyAdmin()")
    public ResponseEntity<ApiResponse<List<CourierProfileResponse>>> listByStore(
            @RequestParam UUID storeId,
            @RequestParam(defaultValue = "false") boolean activeOnly) {
        return service.listByStore(storeId, activeOnly);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('courier:profile:read') or @rbacPolicy.legacyAdmin()")
    public ResponseEntity<ApiResponse<CourierProfileResponse>> getById(@PathVariable UUID id) {
        return service.getById(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('courier:profile:create') or @rbacPolicy.legacyAdmin()")
    public ResponseEntity<ApiResponse<CourierProfileResponse>> create(
            @Valid @RequestBody CreateCourierProfileRequest req) {
        return service.create(req);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('courier:profile:update') or @rbacPolicy.legacyAdmin()")
    public ResponseEntity<ApiResponse<CourierProfileResponse>> update(
            @PathVariable UUID id,
            @RequestBody UpdateCourierProfileRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('courier:profile:delete') or @rbacPolicy.legacyAdmin()")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        return service.delete(id);
    }
}
