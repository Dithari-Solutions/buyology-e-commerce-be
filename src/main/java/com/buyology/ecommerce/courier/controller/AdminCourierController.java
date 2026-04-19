package com.buyology.ecommerce.courier.controller;

import com.buyology.ecommerce.courier.CourierServiceClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/couriers")
@Tag(name = "Admin — Couriers", description = "Admin proxy to buyology-courier-service.")
public class AdminCourierController {

    private final CourierServiceClient courierServiceClient;
    private final ObjectMapper         objectMapper;

    public AdminCourierController(
            CourierServiceClient courierServiceClient,
            ObjectMapper objectMapper
    ) {
        this.courierServiceClient = courierServiceClient;
        this.objectMapper         = objectMapper;
    }

    // ── POST /api/admin/couriers ───────────────────────────────────────────────
    // Proxies → POST /api/auth/admin/couriers
    //
    // multipart/form-data parts:
    //   "data"                — JSON (CreateCourierRequest fields)
    //   "profileImage"        — profile photo       (JPEG/PNG/WebP, ≤10 MB, optional)
    //   "vehicleRegistration" — registration doc    (JPEG/PNG/WebP, ≤10 MB, optional)
    //   "drivingLicenceFront" — licence front image (JPEG/PNG/WebP, ≤10 MB, required for SCOOTER/CAR)
    //   "drivingLicenceBack"  — licence back image  (JPEG/PNG/WebP, ≤10 MB, required for SCOOTER/CAR)

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'COURIER_ADMIN')")
    @Operation(summary = "Create a new courier — multipart form")
    public ResponseEntity<Object> createCourier(
            @RequestPart("data") String dataJson,
            @RequestPart(value = "profileImage",        required = false) MultipartFile profileImage,
            @RequestPart(value = "vehicleRegistration", required = false) MultipartFile vehicleRegistration,
            @RequestPart(value = "drivingLicenceFront", required = false) MultipartFile drivingLicenceFront,
            @RequestPart(value = "drivingLicenceBack",  required = false) MultipartFile drivingLicenceBack,
            HttpServletRequest httpRequest
    ) throws IOException {
        MultiValueMap<String, HttpEntity<?>> body = new LinkedMultiValueMap<>();
        body.add("data", new HttpEntity<>(dataJson, jsonHeaders()));
        addFilePart(body, "profileImage",        profileImage);
        addFilePart(body, "vehicleRegistration", vehicleRegistration);
        addFilePart(body, "drivingLicenceFront", drivingLicenceFront);
        addFilePart(body, "drivingLicenceBack",  drivingLicenceBack);

        return parsed(courierServiceClient.forwardMultipart(
                "/api/auth/admin/couriers", body, adminId(), clientIp(httpRequest)));
    }

    // ── GET /api/admin/couriers/map ───────────────────────────────────────────
    // Proxies → GET /api/v1/couriers/map
    // Query params forwarded as-is: status, vehicleType, isAvailable

    @GetMapping("/map")
    @PreAuthorize("hasAnyRole('ADMIN', 'COURIER_ADMIN')")
    @Operation(summary = "All couriers with latest GPS location for map display")
    public ResponseEntity<Object> getCouriersForMap(HttpServletRequest httpRequest) {
        return parsed(courierServiceClient.forwardNoBody(
                "GET", "/api/v1/couriers/map",
                httpRequest.getQueryString(), adminId(), clientIp(httpRequest)));
    }

    // ── GET /api/admin/couriers ────────────────────────────────────────────────
    // Proxies → GET /api/v1/couriers
    // Query params forwarded as-is: status, vehicleType, isAvailable, page, size, sort

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'COURIER_ADMIN')")
    @Operation(summary = "List couriers with optional filters")
    public ResponseEntity<Object> listCouriers(HttpServletRequest httpRequest) {
        return parsed(courierServiceClient.forwardNoBody(
                "GET", "/api/v1/couriers",
                httpRequest.getQueryString(), adminId(), clientIp(httpRequest)));
    }

    // ── GET /api/admin/couriers/{id} ───────────────────────────────────────────
    // Proxies → GET /api/v1/couriers/{id}

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COURIER_ADMIN')")
    @Operation(summary = "Get courier by ID — includes image URLs")
    public ResponseEntity<Object> getCourier(
            @PathVariable UUID id,
            HttpServletRequest httpRequest
    ) {
        return parsed(courierServiceClient.forwardNoBody(
                "GET", "/api/v1/couriers/" + id,
                null, adminId(), clientIp(httpRequest)));
    }

    // ── PATCH /api/admin/couriers/{id} ─────────────────────────────────────────
    // Proxies → PATCH /api/v1/couriers/{id}
    //
    // multipart/form-data parts (all optional):
    //   "data"               — JSON (UpdateCourierRequest fields)
    //   "profileImage"       — new profile photo         (JPEG/PNG/WebP, ≤10 MB)
    //   "drivingLicenceImage"— new driving licence image  (JPEG/PNG/WebP, ≤10 MB)

    @PatchMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'COURIER_ADMIN')")
    @Operation(summary = "Update courier profile fields and/or images — multipart form")
    public ResponseEntity<Object> updateCourier(
            @PathVariable UUID id,
            @RequestPart("data") String dataJson,
            @RequestPart(value = "profileImage",        required = false) MultipartFile profileImage,
            @RequestPart(value = "drivingLicenceImage", required = false) MultipartFile drivingLicenceImage,
            HttpServletRequest httpRequest
    ) throws IOException {
        MultiValueMap<String, HttpEntity<?>> body = new LinkedMultiValueMap<>();
        body.add("data", new HttpEntity<>(dataJson, jsonHeaders()));
        addFilePart(body, "profileImage",        profileImage);
        addFilePart(body, "drivingLicenceImage", drivingLicenceImage);

        return parsed(courierServiceClient.forwardMultipartPatch(
                "/api/v1/couriers/" + id, body, adminId(), clientIp(httpRequest)));
    }

    // ── PATCH /api/admin/couriers/{id}/status ──────────────────────────────────
    // Proxies → PATCH /api/v1/couriers/{id}/status
    // Body: { "status": "ACTIVE" | "OFFLINE" | "SUSPENDED" }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'COURIER_ADMIN')")
    @Operation(summary = "Update courier operational status")
    public ResponseEntity<Object> updateStatus(
            @PathVariable UUID id,
            @RequestBody Object body,
            HttpServletRequest httpRequest
    ) {
        return parsed(courierServiceClient.forwardJson(
                "PATCH", "/api/v1/couriers/" + id + "/status",
                body, adminId(), clientIp(httpRequest)));
    }

    // ── PATCH /api/admin/couriers/{id}/availability ────────────────────────────
    // Proxies → PATCH /api/v1/couriers/{id}/availability
    // Body: { "available": true | false }

    @PatchMapping("/{id}/availability")
    @PreAuthorize("hasAnyRole('ADMIN', 'COURIER_ADMIN')")
    @Operation(summary = "Toggle courier availability")
    public ResponseEntity<Object> updateAvailability(
            @PathVariable UUID id,
            @RequestBody Object body,
            HttpServletRequest httpRequest
    ) {
        return parsed(courierServiceClient.forwardJson(
                "PATCH", "/api/v1/couriers/" + id + "/availability",
                body, adminId(), clientIp(httpRequest)));
    }

    // ── DELETE /api/admin/couriers/{id} ────────────────────────────────────────
    // Proxies → DELETE /api/v1/couriers/{id}

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COURIER_ADMIN')")
    @Operation(summary = "Soft-delete a courier")
    public ResponseEntity<Object> deleteCourier(
            @PathVariable UUID id,
            HttpServletRequest httpRequest
    ) {
        return parsed(courierServiceClient.forwardNoBody(
                "DELETE", "/api/v1/couriers/" + id,
                null, adminId(), clientIp(httpRequest)));
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    /** Returns the authenticated admin's user ID (set by JwtAuthenticationFilter). */
    private String adminId() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private void addFilePart(MultiValueMap<String, HttpEntity<?>> body,
                              String partName, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) return;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                file.getContentType() != null ? file.getContentType() : "application/octet-stream"));
        headers.setContentDispositionFormData(partName, file.getOriginalFilename());
        body.add(partName, new HttpEntity<>(file.getResource(), headers));
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private ResponseEntity<Object> parsed(ResponseEntity<String> upstream) {
        try {
            Object parsed = objectMapper.readValue(upstream.getBody(), Object.class);
            return ResponseEntity.status(upstream.getStatusCode()).body(parsed);
        } catch (Exception e) {
            return ResponseEntity.status(upstream.getStatusCode()).body(upstream.getBody());
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
