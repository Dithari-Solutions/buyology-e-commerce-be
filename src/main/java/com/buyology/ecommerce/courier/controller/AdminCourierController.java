package com.buyology.ecommerce.courier.controller;

import com.buyology.ecommerce.courier.CourierServiceClient;
import com.buyology.ecommerce.courier.KeycloakTokenProvider;
import com.buyology.ecommerce.courier.dto.CreateCourierRequest;
import com.buyology.ecommerce.courier.dto.UpdateCourierStatusRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/couriers")
@Tag(name = "Admin — Couriers", description = "Admin operations for managing courier accounts.")
public class AdminCourierController {

    private final CourierServiceClient courierServiceClient;
    private final KeycloakTokenProvider keycloakTokenProvider;
    private final ObjectMapper objectMapper;

    public AdminCourierController(
            CourierServiceClient courierServiceClient,
            KeycloakTokenProvider keycloakTokenProvider,
            ObjectMapper objectMapper
    ) {
        this.courierServiceClient  = courierServiceClient;
        this.keycloakTokenProvider = keycloakTokenProvider;
        this.objectMapper          = objectMapper;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'COURIER_ADMIN')")
    @Operation(summary = "Create a new courier (admin only)")
    public ResponseEntity<Object> createCourier(
            @Valid @RequestBody CreateCourierRequest request,
            HttpServletRequest httpRequest
    ) {
        String bearerToken = keycloakTokenProvider.getBearerToken();
        String clientIp    = resolveClientIp(httpRequest);
        return parseUpstream(courierServiceClient.createCourier(request, bearerToken, clientIp));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'COURIER_ADMIN')")
    @Operation(summary = "List couriers (admin only)")
    public ResponseEntity<Object> listCouriers(
            HttpServletRequest httpRequest,
            @Parameter(description = "Page number (0-based)") @RequestParam(required = false) Integer page,
            @Parameter(description = "Page size")             @RequestParam(required = false) Integer size,
            @Parameter(description = "Filter by status: ACTIVE, SUSPENDED, INACTIVE, PENDING")
            @RequestParam(required = false) String status,
            @Parameter(description = "Filter by vehicle type: BICYCLE, FOOT, SCOOTER, CAR")
            @RequestParam(required = false) String vehicleType,
            @Parameter(description = "Search by name or phone")
            @RequestParam(required = false) String search
    ) {
        String bearerToken = keycloakTokenProvider.getBearerToken();
        String clientIp    = resolveClientIp(httpRequest);
        return parseUpstream(courierServiceClient.listCouriers(
                bearerToken, clientIp, page, size, status, vehicleType, search));
    }

    @GetMapping("/{courierId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COURIER_ADMIN')")
    @Operation(summary = "Get courier details by ID (admin only)")
    public ResponseEntity<Object> getCourierById(
            @PathVariable String courierId,
            HttpServletRequest httpRequest
    ) {
        String bearerToken = keycloakTokenProvider.getBearerToken();
        String clientIp    = resolveClientIp(httpRequest);
        return parseUpstream(courierServiceClient.getCourierById(courierId, bearerToken, clientIp));
    }

    @PatchMapping("/{courierId}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'COURIER_ADMIN')")
    @Operation(summary = "Update courier status (admin only)")
    public ResponseEntity<Object> updateCourierStatus(
            @PathVariable String courierId,
            @Valid @RequestBody UpdateCourierStatusRequest request,
            HttpServletRequest httpRequest
    ) {
        String bearerToken = keycloakTokenProvider.getBearerToken();
        String clientIp    = resolveClientIp(httpRequest);
        return parseUpstream(courierServiceClient.updateCourierStatus(
                courierId, request, bearerToken, clientIp));
    }

    @DeleteMapping("/{courierId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a courier account (ADMIN only)")
    public ResponseEntity<Object> deleteCourier(
            @PathVariable String courierId,
            HttpServletRequest httpRequest
    ) {
        String bearerToken = keycloakTokenProvider.getBearerToken();
        String clientIp    = resolveClientIp(httpRequest);
        return parseUpstream(courierServiceClient.deleteCourier(courierId, bearerToken, clientIp));
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
