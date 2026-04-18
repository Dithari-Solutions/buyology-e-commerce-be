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

import java.util.UUID;

/**
 * Admin-only order management endpoints.
 * Role check is enforced by @PreAuthorize("hasRole('ADMIN')") on each method.
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
    @PreAuthorize("hasAnyRole('ADMIN', 'STORE_ADMIN')")
    public ResponseEntity<ApiResponse<Page<OrderSummaryResponse>>> listAllOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) DeliveryMethod deliveryMethod,
            @RequestParam(required = false) java.util.UUID storeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
                orderService.listAllOrders(status, deliveryMethod, storeId, page, size),
                "Orders fetched successfully");
    }

    /**
     * Get full order detail for any order.
     */
    @GetMapping("/{orderId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(@PathVariable UUID orderId) {
        return ApiResponse.success(
                orderService.getOrderForAdmin(orderId),
                "Order fetched successfully");
    }

    /**
     * Get full order detail including delivery proof (photos) from courier service.
     */
    @GetMapping("/{orderId}/with-proof")
    @PreAuthorize("hasRole('ADMIN')")
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
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> updateStatus(
            @AuthenticationPrincipal UUID adminUserId,
            @PathVariable UUID orderId,
            @Valid @RequestBody AdminStatusUpdateRequest request) {
        return ApiResponse.success(
                orderService.adminUpdateStatus(orderId, adminUserId, request),
                "Order status updated successfully");
    }

    /**
     * Add a tracking event and optionally set carrier tracking code.
     * For REGULAR orders being marked as SHIPPED, trackingCode is required.
     */
    @PostMapping("/{orderId}/tracking")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> addTracking(
            @AuthenticationPrincipal UUID adminUserId,
            @PathVariable UUID orderId,
            @Valid @RequestBody AdminTrackingUpdateRequest request) {
        return ApiResponse.success(
                orderService.adminAddTracking(orderId, adminUserId, request),
                "Tracking updated successfully");
    }
}
