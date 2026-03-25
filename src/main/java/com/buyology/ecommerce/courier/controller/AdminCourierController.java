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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/couriers")
@Tag(name = "Admin — Couriers", description = "Admin operations for managing courier accounts.")
public class AdminCourierController {

    private static final Logger log = LoggerFactory.getLogger(AdminCourierController.class);

    private final CourierServiceClient courierServiceClient;
    private final ObjectMapper objectMapper;

    public AdminCourierController(
            CourierServiceClient courierServiceClient,
            ObjectMapper objectMapper
    ) {
        this.courierServiceClient = courierServiceClient;
        this.objectMapper         = objectMapper;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'COURIER_ADMIN')")
    @Operation(summary = "Create a new courier (admin only)")
    public ResponseEntity<Object> createCourier(
            @Valid @RequestBody CreateCourierRequest request,
            HttpServletRequest httpRequest
    ) {
        String clientIp = resolveClientIp(httpRequest);
        logIncomingRequest("POST", "/api/admin/couriers", clientIp);
        ResponseEntity<String> upstream = courierServiceClient.createCourier(request, clientIp);
        logUpstreamResponse("POST", "/api/admin/couriers", upstream);
        return parseUpstream(upstream);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'COURIER_ADMIN')")
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
        String clientIp = resolveClientIp(httpRequest);
        log.info("[COURIER] GET /api/admin/couriers ip={} page={} size={} status={} vehicleType={} search={}",
                clientIp, page, size, status, vehicleType, search);
        logAuthContext();
        ResponseEntity<String> upstream = courierServiceClient.listCouriers(clientIp, page, size, status, vehicleType, search);
        logUpstreamResponse("GET", "/api/admin/couriers", upstream);
        return parseUpstream(upstream);
    }

    @GetMapping("/{courierId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'COURIER_ADMIN')")
    @Operation(summary = "Get courier details by ID (admin only)")
    public ResponseEntity<Object> getCourierById(
            @PathVariable String courierId,
            HttpServletRequest httpRequest
    ) {
        String clientIp = resolveClientIp(httpRequest);
        logIncomingRequest("GET", "/api/admin/couriers/" + courierId, clientIp);
        ResponseEntity<String> upstream = courierServiceClient.getCourierById(courierId, clientIp);
        logUpstreamResponse("GET", "/api/admin/couriers/" + courierId, upstream);
        return parseUpstream(upstream);
    }

    @PatchMapping("/{courierId}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN', 'COURIER_ADMIN')")
    @Operation(summary = "Update courier status (admin only)")
    public ResponseEntity<Object> updateCourierStatus(
            @PathVariable String courierId,
            @Valid @RequestBody UpdateCourierStatusRequest request,
            HttpServletRequest httpRequest
    ) {
        String clientIp = resolveClientIp(httpRequest);
        logIncomingRequest("PATCH", "/api/admin/couriers/" + courierId + "/status", clientIp);
        ResponseEntity<String> upstream = courierServiceClient.updateCourierStatus(courierId, request, clientIp);
        logUpstreamResponse("PATCH", "/api/admin/couriers/" + courierId + "/status", upstream);
        return parseUpstream(upstream);
    }

    @DeleteMapping("/{courierId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERADMIN')")
    @Operation(summary = "Delete a courier account (ADMIN/SUPERADMIN only)")
    public ResponseEntity<Object> deleteCourier(
            @PathVariable String courierId,
            HttpServletRequest httpRequest
    ) {
        String clientIp = resolveClientIp(httpRequest);
        logIncomingRequest("DELETE", "/api/admin/couriers/" + courierId, clientIp);
        ResponseEntity<String> upstream = courierServiceClient.deleteCourier(courierId, clientIp);
        logUpstreamResponse("DELETE", "/api/admin/couriers/" + courierId, upstream);
        return parseUpstream(upstream);
    }

    // -------------------------------------------------------------------------

    private void logIncomingRequest(String method, String path, String clientIp) {
        log.info("[COURIER] {} {} ip={}", method, path, clientIp);
        logAuthContext();
    }

    private void logAuthContext() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            log.warn("[COURIER] No authentication in SecurityContext");
        } else {
            log.info("[COURIER] principal={} authenticated={} authorities={}",
                    auth.getName(), auth.isAuthenticated(), auth.getAuthorities());
        }
    }

    private void logUpstreamResponse(String method, String path, ResponseEntity<String> upstream) {
        int status = upstream.getStatusCode().value();
        if (status >= 400) {
            log.warn("[COURIER] Upstream {} {} → {} body={}", method, path, status, upstream.getBody());
        } else {
            log.info("[COURIER] Upstream {} {} → {}", method, path, status);
        }
    }

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
