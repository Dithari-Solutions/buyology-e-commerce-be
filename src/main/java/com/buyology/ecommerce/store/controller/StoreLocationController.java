package com.buyology.ecommerce.store.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.store.dto.CreateOperatingHoursRequest;
import com.buyology.ecommerce.store.dto.CreateStoreLocationRequest;
import com.buyology.ecommerce.store.dto.DeliveryInfoResponse;
import com.buyology.ecommerce.store.dto.OperatingHoursResponse;
import com.buyology.ecommerce.store.dto.StoreLocationResponse;
import com.buyology.ecommerce.store.dto.UpdateOperatingHoursRequest;
import com.buyology.ecommerce.store.dto.UpdateStoreLocationRequest;
import com.buyology.ecommerce.store.service.StoreLocationService;
import com.buyology.ecommerce.store.service.StoreOperatingHoursService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/stores")
@Tag(name = "Store Location", description = "APIs for managing store locations and their operating hours")
public class StoreLocationController {

    private final StoreLocationService locationService;
    private final StoreOperatingHoursService hoursService;

    public StoreLocationController(StoreLocationService locationService,
                                   StoreOperatingHoursService hoursService) {
        this.locationService = locationService;
        this.hoursService = hoursService;
    }

    // ── Delivery info (public) ────────────────────────────────────────────────

    @Operation(summary = "Get cities and stores with delivery time for a country",
            description = "Pass the customer's lat/lng to see which stores offer ≤30 min express delivery.")
    @GetMapping("/delivery-info")
    public ResponseEntity<ApiResponse<DeliveryInfoResponse>> getDeliveryInfo(
            @RequestParam String country,
            @RequestParam double lat,
            @RequestParam double lng) {
        return locationService.getDeliveryInfo(country, lat, lng);
    }

    // ── Location endpoints ────────────────────────────────────────────────────

    @Operation(summary = "Add a location (branch) to a store",
            description = "When isPrimary=true the existing primary branch is automatically demoted.")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('store:location:create') or @rbacPolicy.legacyAdmin()")
    @PostMapping("/{storeId}/locations")
    public ResponseEntity<ApiResponse<StoreLocationResponse>> createLocation(
            @PathVariable UUID storeId,
            @RequestBody @Valid CreateStoreLocationRequest request) {
        return locationService.createLocation(storeId, request);
    }

    @Operation(summary = "Get all locations for a store")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('store:location:read') or @rbacPolicy.legacyAdmin()")
    @GetMapping("/{storeId}/locations")
    public ResponseEntity<ApiResponse<List<StoreLocationResponse>>> getLocationsByStore(
            @PathVariable UUID storeId) {
        return locationService.getLocationsByStore(storeId);
    }

    @Operation(summary = "Get a location by ID")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('store:location:read') or @rbacPolicy.legacyAdmin()")
    @GetMapping("/locations/{locationId}")
    public ResponseEntity<ApiResponse<StoreLocationResponse>> getLocationById(
            @PathVariable UUID locationId) {
        return locationService.getLocationById(locationId);
    }

    @Operation(summary = "Update a store location")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('store:location:update') or @rbacPolicy.legacyAdmin()")
    @PatchMapping("/locations/{locationId}")
    public ResponseEntity<ApiResponse<StoreLocationResponse>> updateLocation(
            @PathVariable UUID locationId,
            @RequestBody @Valid UpdateStoreLocationRequest request) {
        return locationService.updateLocation(locationId, request);
    }

    @Operation(summary = "Deactivate a store location")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('store:location:delete') or @rbacPolicy.legacyAdmin()")
    @DeleteMapping("/locations/{locationId}")
    public ResponseEntity<ApiResponse<Void>> deleteLocation(@PathVariable UUID locationId) {
        return locationService.deleteLocation(locationId);
    }

    // ── Operating hours sub-resource ─────────────────────────────────────────

    @Operation(summary = "Set operating hours for a day at a location")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('store:location:update') or @rbacPolicy.legacyAdmin()")
    @PostMapping("/locations/{locationId}/hours")
    public ResponseEntity<ApiResponse<OperatingHoursResponse>> createHours(
            @PathVariable UUID locationId,
            @RequestBody @Valid CreateOperatingHoursRequest request) {
        return hoursService.createHours(locationId, request);
    }

    @Operation(summary = "Get all operating hours for a location")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('store:location:read') or @rbacPolicy.legacyAdmin()")
    @GetMapping("/locations/{locationId}/hours")
    public ResponseEntity<ApiResponse<List<OperatingHoursResponse>>> getHoursByLocation(
            @PathVariable UUID locationId) {
        return hoursService.getHoursByLocation(locationId);
    }

    @Operation(summary = "Get a specific operating hours entry by ID")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('store:location:read') or @rbacPolicy.legacyAdmin()")
    @GetMapping("/hours/{hoursId}")
    public ResponseEntity<ApiResponse<OperatingHoursResponse>> getHoursById(
            @PathVariable UUID hoursId) {
        return hoursService.getHoursById(hoursId);
    }

    @Operation(summary = "Update an operating hours entry")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('store:location:update') or @rbacPolicy.legacyAdmin()")
    @PatchMapping("/hours/{hoursId}")
    public ResponseEntity<ApiResponse<OperatingHoursResponse>> updateHours(
            @PathVariable UUID hoursId,
            @RequestBody @Valid UpdateOperatingHoursRequest request) {
        return hoursService.updateHours(hoursId, request);
    }

    @Operation(summary = "Delete an operating hours entry")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('store:location:update') or @rbacPolicy.legacyAdmin()")
    @DeleteMapping("/hours/{hoursId}")
    public ResponseEntity<ApiResponse<Void>> deleteHours(@PathVariable UUID hoursId) {
        return hoursService.deleteHours(hoursId);
    }
}
