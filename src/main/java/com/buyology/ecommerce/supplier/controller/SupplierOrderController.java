package com.buyology.ecommerce.supplier.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.order.domain.enums.OrderStatus;
import com.buyology.ecommerce.order.dto.OrderSummaryResponse;
import com.buyology.ecommerce.order.service.OrderService;
import com.buyology.ecommerce.supplier.domain.Supplier;
import com.buyology.ecommerce.supplier.repository.SupplierRepository;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Supplier-facing order list: orders that contain at least one of the current
 * supplier's items. Read-only — fulfilment actions stay with admins.
 */
@RestController
@RequestMapping("/api/supplier/orders")
public class SupplierOrderController {

    private final OrderService orderService;
    private final SupplierRepository supplierRepository;

    public SupplierOrderController(OrderService orderService, SupplierRepository supplierRepository) {
        this.orderService = orderService;
        this.supplierRepository = supplierRepository;
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPPLIER')")
    public ResponseEntity<ApiResponse<Page<OrderSummaryResponse>>> myOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Supplier supplier = resolveCurrentSupplier();
        if (supplier == null) {
            return ApiResponse.failure(HttpStatus.FORBIDDEN, "Supplier account not found");
        }
        return ApiResponse.success(
                orderService.listOrdersForSupplier(supplier.getId(), status, page, size),
                "Orders fetched successfully");
    }

    private Supplier resolveCurrentSupplier() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !(auth.getPrincipal() instanceof UUID userId)) return null;
            return supplierRepository.findByUserId(userId).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
