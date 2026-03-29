package com.buyology.ecommerce.order.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.order.dto.CreateOrderRequest;
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
 * Customer-facing order endpoints.
 * All endpoints require an authenticated user; ownership is enforced in the service layer.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Create a new order from a checked-out cart.
     * The caller must own the cart and the delivery address.
     * The order starts in PENDING_PAYMENT until the payment webhook confirms success.
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @AuthenticationPrincipal UUID userId,
            @RequestHeader("X-Auth-Credential-Id") UUID authCredentialId,
            @Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.created(
                orderService.createOrder(userId, authCredentialId, request),
                "Order created successfully");
    }

    /**
     * Get a single order by ID. Returns 404 if the order does not belong to the caller.
     */
    @GetMapping("/{orderId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID orderId) {
        return ApiResponse.success(
                orderService.getOrderForCustomer(orderId, userId),
                "Order fetched successfully");
    }

    /**
     * List all orders for the authenticated customer (most recent first).
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<OrderSummaryResponse>>> listMyOrders(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(
                orderService.listCustomerOrders(userId, page, size),
                "Orders fetched successfully");
    }
}
