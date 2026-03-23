package com.buyology.ecommerce.courier.controller;

import com.buyology.ecommerce.courier.CourierServiceClient;
import com.buyology.ecommerce.courier.dto.CreateCourierRequest;
import com.buyology.ecommerce.courier.dto.UpdateCourierStatusRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/couriers")
@Tag(name = "Admin — Couriers", description = "Admin operations for managing courier accounts.")
public class AdminCourierController {

    private final CourierServiceClient courierServiceClient;
    private final ObjectMapper objectMapper;

    public AdminCourierController(CourierServiceClient courierServiceClient, ObjectMapper objectMapper) {
        this.courierServiceClient = courierServiceClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new courier account.
     *
     * The request is validated here first, then forwarded to the courier service.
     * The admin's JWT is forwarded so the courier service can record which admin
     * performed the action in its audit log.
     *
     * Required role: ADMIN or COURIER_ADMIN.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'COURIER_ADMIN')")
    @Operation(summary = "Create a new courier (admin only)")
    public ResponseEntity<Object> createCourier(
            @Valid @RequestBody CreateCourierRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken,
            HttpServletRequest httpRequest
    ) {
        String clientIp = resolveClientIp(httpRequest);
        ResponseEntity<String> upstream = courierServiceClient.createCourier(request, bearerToken, clientIp);
        return parseUpstream(upstream);
    }

    /**
     * List all couriers with optional filtering and pagination.
     *
     * Required role: ADMIN or COURIER_ADMIN.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'COURIER_ADMIN')")
    @Operation(summary = "List couriers (admin only)")
    public ResponseEntity<Object> listCouriers(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken,
            HttpServletRequest httpRequest,
            @Parameter(description = "Page number (0-based)") @RequestParam(required = false) Integer page,
            @Parameter(description = "Page size") @RequestParam(required = false) Integer size,
            @Parameter(description = "Filter by status: ACTIVE, SUSPENDED, INACTIVE, PENDING")
            @RequestParam(required = false) String status,
            @Parameter(description = "Filter by vehicle type: BICYCLE, FOOT, SCOOTER, CAR")
            @RequestParam(required = false) String vehicleType,
            @Parameter(description = "Search by name or phone")
            @RequestParam(required = false) String search
    ) {
        String clientIp = resolveClientIp(httpRequest);
        ResponseEntity<String> upstream = courierServiceClient.listCouriers(
                bearerToken, clientIp, page, size, status, vehicleType, search);
        return parseUpstream(upstream);
    }

    /**
     * Get a single courier's full profile by their ID.
     *
     * Required role: ADMIN or COURIER_ADMIN.
     */
    @GetMapping("/{courierId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COURIER_ADMIN')")
    @Operation(summary = "Get courier details by ID (admin only)")
    public ResponseEntity<Object> getCourierById(
            @PathVariable String courierId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken,
            HttpServletRequest httpRequest
    ) {
        String clientIp = resolveClientIp(httpRequest);
        ResponseEntity<String> upstream = courierServiceClient.getCourierById(courierId, bearerToken, clientIp);
        return parseUpstream(upstream);
    }

    /**
     * Update a courier's account status (activate, suspend, deactivate, etc.).
     *
     * Required role: ADMIN or COURIER_ADMIN.
     */
    @PatchMapping("/{courierId}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'COURIER_ADMIN')")
    @Operation(summary = "Update courier status (admin only)")
    public ResponseEntity<Object> updateCourierStatus(
            @PathVariable String courierId,
            @Valid @RequestBody UpdateCourierStatusRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken,
            HttpServletRequest httpRequest
    ) {
        String clientIp = resolveClientIp(httpRequest);
        ResponseEntity<String> upstream = courierServiceClient.updateCourierStatus(
                courierId, request, bearerToken, clientIp);
        return parseUpstream(upstream);
    }

    /**
     * Delete (permanently deactivate) a courier account.
     *
     * Required role: ADMIN only — COURIER_ADMIN cannot delete accounts.
     */
    @DeleteMapping("/{courierId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a courier account (ADMIN only)")
    public ResponseEntity<Object> deleteCourier(
            @PathVariable String courierId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String bearerToken,
            HttpServletRequest httpRequest
    ) {
        String clientIp = resolveClientIp(httpRequest);
        ResponseEntity<String> upstream = courierServiceClient.deleteCourier(courierId, bearerToken, clientIp);
        return parseUpstream(upstream);
    }

    // -------------------------------------------------------------------------

    private ResponseEntity<Object> parseUpstream(ResponseEntity<String> upstream) {
        try {
            Object parsed = objectMapper.readValue(upstream.getBody(), Object.class);
            return ResponseEntity.status(upstream.getStatusCode()).body(parsed);
        } catch (Exception e) {
            return ResponseEntity.status(upstream.getStatusCode()).body(upstream.getBody());
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
