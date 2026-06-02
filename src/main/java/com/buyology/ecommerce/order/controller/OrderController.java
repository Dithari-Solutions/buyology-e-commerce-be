package com.buyology.ecommerce.order.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.order.dto.CreateOrderRequest;
import com.buyology.ecommerce.order.dto.OrderResponse;
import com.buyology.ecommerce.order.dto.OrderSummaryResponse;
import com.buyology.ecommerce.membership.service.B2bCreditOrderService;
import com.buyology.ecommerce.order.service.OrderService;
import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import com.buyology.ecommerce.common.utils.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Customer-facing order endpoints.
 * All endpoints require an authenticated user; ownership is enforced in the service layer.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    /** Hard cap on page size for order listings to prevent unbounded queries (DoS). */
    private static final int MAX_PAGE_SIZE = 100;

    private final OrderService orderService;
    private final B2bCreditOrderService b2bCreditOrderService;
    private final AuthCredentialRepository authCredentialRepository;

    public OrderController(OrderService orderService,
                           B2bCreditOrderService b2bCreditOrderService,
                           AuthCredentialRepository authCredentialRepository) {
        this.orderService = orderService;
        this.b2bCreditOrderService = b2bCreditOrderService;
        this.authCredentialRepository = authCredentialRepository;
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
        // The authCredentialId is client-supplied — verify it actually belongs to the
        // authenticated user so one user cannot place orders under another's credential.
        requireOwnedCredential(userId, authCredentialId);
        return ApiResponse.created(
                orderService.createOrder(userId, authCredentialId, request),
                "Order created successfully");
    }

    /**
     * Resolves the client-supplied authCredentialId to its owning userId and asserts
     * it matches the authenticated principal (admins bypass). Throws 403 otherwise.
     */
    private void requireOwnedCredential(UUID userId, UUID authCredentialId) {
        UUID ownerUserId = authCredentialRepository.findById(authCredentialId)
                .map(c -> c.getUserId())
                .orElse(null);
        SecurityUtils.requireSelfOrAdmin(ownerUserId);
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
        // Clamp the page size to a sane maximum to prevent unbounded result sets (DoS).
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return ApiResponse.success(
                orderService.listCustomerOrders(userId, page, safeSize),
                "Orders fetched successfully");
    }

    /**
     * Cancel an order placed by the authenticated customer.
     */
    /**
     * Apply B2B credit to a pending order.
     *
     * Body: {@code { "amount": optional decimal in wallet currency }}.
     * If {@code amount} is null/omitted, applies the full order total (capped at the wallet balance).
     * Otherwise applies that amount (capped at order total + wallet balance), and the order
     * remains in PENDING_PAYMENT for the remainder, which the frontend then settles via Paymob.
     */
    public record PayWithCreditRequest(java.math.BigDecimal amount) {}

    @PostMapping("/{orderId}/pay-with-credit")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> payWithCredit(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID orderId,
            @RequestBody(required = false) PayWithCreditRequest body) {
        return b2bCreditOrderService.payOrderWithCredit(
                userId, orderId, body == null ? null : body.amount());
    }

    @PostMapping("/{orderId}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID orderId,
            @RequestBody(required = false) CancelOrderRequest request) {
        String reason = request != null ? request.reason() : null;
        return ApiResponse.success(
                orderService.customerCancelOrder(orderId, userId, reason),
                "Order cancelled successfully");
    }

    public record CancelOrderRequest(String reason) {}
}
