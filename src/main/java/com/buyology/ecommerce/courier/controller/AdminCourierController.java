package com.buyology.ecommerce.courier.controller;

import com.buyology.ecommerce.courier.CourierServiceClient;
import com.buyology.ecommerce.courier.dto.CreateCourierRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
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
