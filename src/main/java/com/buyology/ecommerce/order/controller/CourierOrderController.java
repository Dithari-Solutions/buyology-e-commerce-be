package com.buyology.ecommerce.order.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.order.dto.CourierTrackingUpdateRequest;
import com.buyology.ecommerce.order.dto.OrderResponse;
import com.buyology.ecommerce.order.dto.OrderSummaryResponse;
import com.buyology.ecommerce.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Courier-facing endpoints for LOCAL_EXPRESS delivery management.
 * Requires the caller to have the 'courier:update' permission
 * (assigned via the role/permission system in the role module).
 */
@RestController
@RequestMapping("/api/courier/orders")
public class CourierOrderController {

    private final OrderService orderService;

    public CourierOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * List all LOCAL_EXPRESS orders assigned to the authenticated courier.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('courier:update')")
    public ResponseEntity<ApiResponse<List<OrderSummaryResponse>>> listMyOrders(
            @AuthenticationPrincipal UUID courierUserId) {
        return ApiResponse.success(
                orderService.listCourierOrders(courierUserId),
                "Assigned orders fetched successfully");
    }

    /**
     * Update tracking for an assigned LOCAL_EXPRESS order.
     * Allowed statuses: PICKED_UP, IN_TRANSIT, DELIVERED, FAILED.
     * Latitude/longitude are captured for real-time map tracking support.
     */
    @PostMapping("/{orderId}/tracking")
    @PreAuthorize("hasAuthority('courier:update')")
    public ResponseEntity<ApiResponse<OrderResponse>> updateTracking(
            @AuthenticationPrincipal UUID courierUserId,
            @PathVariable UUID orderId,
            @Valid @RequestBody CourierTrackingUpdateRequest request) {
        return ApiResponse.success(
                orderService.courierUpdateTracking(orderId, courierUserId, request),
                "Tracking updated successfully");
    }
}
