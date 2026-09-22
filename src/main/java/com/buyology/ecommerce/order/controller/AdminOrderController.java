package com.buyology.ecommerce.order.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.order.domain.enums.DeliveryMethod;
import com.buyology.ecommerce.order.domain.enums.OrderStatus;
import com.buyology.ecommerce.order.dto.AdminStatusUpdateRequest;
import com.buyology.ecommerce.order.dto.AdminTrackingUpdateRequest;
import com.buyology.ecommerce.order.dto.OrderAdminResponse;
import com.buyology.ecommerce.order.dto.OrderResponse;
import com.buyology.ecommerce.order.dto.OrderSummaryResponse;
import com.buyology.ecommerce.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Admin-only order management endpoints.
 * The /api/admin/** prefix is also locked to authenticated users via SecurityConfig.
 */
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final OrderService orderService;

    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * List all orders with optional filtering by status, delivery method, and store.
     */
    @GetMapping
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('order:read') or @rbacPolicy.legacyAdmin()")
    public ResponseEntity<ApiResponse<Page<OrderSummaryResponse>>> listAllOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) DeliveryMethod deliveryMethod,
            @RequestParam(required = false) java.util.UUID storeId,
            @RequestParam(required = false) java.util.UUID supplierId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
                orderService.listAllOrders(status, deliveryMethod, storeId, supplierId, page, size),
                "Orders fetched successfully");
    }

    /**
     * Get full order detail for any order.
     */
    @GetMapping("/{orderId}")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('order:read') or @rbacPolicy.legacyAdmin()")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(@PathVariable UUID orderId) {
        return ApiResponse.success(
                orderService.getOrderForAdmin(orderId),
                "Order fetched successfully");
    }

    /**
     * Get full order detail including delivery proof (photos) from courier service.
     */
    @GetMapping("/{orderId}/with-proof")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('order:read') or @rbacPolicy.legacyAdmin()")
    public ResponseEntity<ApiResponse<OrderAdminResponse>> getOrderWithProof(
            @AuthenticationPrincipal UUID adminUserId,
            @PathVariable UUID orderId) {
        return ApiResponse.success(
                orderService.getOrderWithProofForAdmin(orderId, adminUserId),
                "Order with proof fetched successfully");
    }

    /**
     * Update order status. For EXPRESS orders, optionally assigns a courier
     * when moving to COURIER_ASSIGNED (pass courierUserId in the request body).
     * Validates the status transition — invalid transitions return HTTP 409.
     */
    @PatchMapping("/{orderId}/status")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('order:status:update') or @rbacPolicy.legacyAdmin()")
    public ResponseEntity<ApiResponse<OrderAdminResponse>> updateStatus(
            @AuthenticationPrincipal UUID adminUserId,
            @PathVariable UUID orderId,
            @Valid @RequestBody AdminStatusUpdateRequest request) {
        orderService.adminUpdateStatus(orderId, adminUserId, request);
        // Re-read, rather than returning what adminUpdateStatus built. That was built before the
        // commit, so it could not know how the Quiqup cancel went (which runs after the commit,
        // on this thread, before we get here) and, being the customer-facing shape, it carried no
        // Quiqup fields at all: the dashboard swapped it in and the order's Quiqup card vanished
        // right when the admin needed to see whether the courier had been stopped.
        return ApiResponse.success(
                orderService.getFreshOrderForAdmin(orderId),
                "Order status updated successfully");
    }

    /**
     * Record that a cash-on-delivery order's money is actually in hand.
     *
     * <p>Deliberately NOT a status change. A cash order's fulfilment and its payment run on
     * different clocks — it is packed, dispatched and delivered while unpaid, and the cash is banked
     * at the end — so there is no point in the status machine where "PAID" belongs. This stamps the
     * collection on the order itself (who, when, how much), which is what
     * {@code Order.isMoneyCollected()} reads and therefore what decides whether there is anything
     * to refund if the order is later cancelled.
     *
     * <p>Body is optional: {@code { "amount": 249.00, "notes": "..." }}. An omitted amount means
     * the order's total was collected in full.
     */
    @PostMapping("/{orderId}/cod-collected")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('order:status:update') or @rbacPolicy.legacyAdmin()")
    public ResponseEntity<ApiResponse<OrderResponse>> recordCashCollected(
            @AuthenticationPrincipal UUID adminUserId,
            @PathVariable UUID orderId,
            @RequestBody(required = false) RecordCashCollectedRequest request) {
        return ApiResponse.success(
                orderService.recordCashOnDeliveryCollected(
                        orderId, adminUserId,
                        request == null ? null : request.amount(),
                        request == null ? null : request.notes()),
                "Cash collection recorded");
    }

    /** @param amount collected, in the order's own currency; null means the full total. */
    public record RecordCashCollectedRequest(java.math.BigDecimal amount, String notes) {}

    /**
     * Assign one of the order's store's couriers to the order (stamps courier name/phone).
     * Body: { "courierProfileId": "<uuid>" }.
     */
    @PatchMapping("/{orderId}/courier")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('order:courier:assign') or @rbacPolicy.legacyAdmin()")
    public ResponseEntity<ApiResponse<OrderResponse>> assignCourier(
            @AuthenticationPrincipal UUID adminUserId,
            @PathVariable UUID orderId,
            @RequestBody java.util.Map<String, java.util.UUID> body) {
        java.util.UUID courierProfileId = body.get("courierProfileId");
        return ApiResponse.success(
                orderService.assignStoreCourier(orderId, adminUserId, courierProfileId),
                "Courier assigned successfully");
    }

    /**
     * Add a tracking event and optionally set carrier tracking code.
     * For REGULAR orders being marked as SHIPPED, trackingCode is required.
     */
    @PostMapping("/{orderId}/tracking")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('order:tracking:update') or @rbacPolicy.legacyAdmin()")
    public ResponseEntity<ApiResponse<OrderResponse>> addTracking(
            @AuthenticationPrincipal UUID adminUserId,
            @PathVariable UUID orderId,
            @Valid @RequestBody AdminTrackingUpdateRequest request) {
        return ApiResponse.success(
                orderService.adminAddTracking(orderId, adminUserId, request),
                "Tracking updated successfully");
    }

    /**
     * Upload a pickup or drop-off proof photo. {@code type} must be PICKUP or DROPOFF.
     * Multipart form: field {@code file} carries the image.
     */
    @PostMapping(value = "/{orderId}/proof/{type}", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('order:tracking:update') or @rbacPolicy.legacyAdmin()")
    public ResponseEntity<ApiResponse<OrderAdminResponse>> uploadProof(
            @AuthenticationPrincipal UUID adminUserId,
            @PathVariable UUID orderId,
            @PathVariable OrderService.ProofType type,
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.success(
                orderService.adminUploadProof(orderId, adminUserId, type, file),
                type.name() + " proof uploaded successfully");
    }
}
