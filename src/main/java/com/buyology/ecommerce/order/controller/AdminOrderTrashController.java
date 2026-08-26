package com.buyology.ecommerce.order.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.order.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * The order trash.
 *
 * <p>SUPERADMIN only, and deliberately not folded into the ordinary admin order endpoints:
 * deleting an order is not a status change, it removes the record from every list including the
 * customer's own history and the revenue report. It is recoverable for 30 days, after which the
 * order is destroyed for good.
 */
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderTrashController {

    private final OrderService orderService;

    public AdminOrderTrashController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** Move an order to the trash. */
    @PreAuthorize("hasRole('SUPERADMIN')")
    @DeleteMapping("/{orderId}")
    public ResponseEntity<ApiResponse<Void>> trash(
            @AuthenticationPrincipal UUID adminUserId, @PathVariable UUID orderId) {
        orderService.trashOrder(orderId, adminUserId);
        return ApiResponse.success(null, "Order moved to trash. It will be deleted in 30 days.");
    }

    /** What is currently in the trash, and when each item will be destroyed. */
    @PreAuthorize("hasRole('SUPERADMIN')")
    @GetMapping("/trash")
    public ResponseEntity<ApiResponse<OrderService.TrashPage>> trash(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(orderService.listTrash(page, size), "Trash fetched");
    }

    /** Bring an order back. */
    @PreAuthorize("hasRole('SUPERADMIN')")
    @PostMapping("/trash/{orderId}/restore")
    public ResponseEntity<ApiResponse<Void>> restore(@PathVariable UUID orderId) {
        orderService.restoreOrder(orderId);
        return ApiResponse.success(null, "Order restored");
    }
}
