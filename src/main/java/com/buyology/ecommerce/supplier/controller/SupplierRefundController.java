package com.buyology.ecommerce.supplier.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.refund.dto.RefundRequestResponse;
import com.buyology.ecommerce.refund.enums.RefundRequestStatus;
import com.buyology.ecommerce.refund.service.RefundRequestService;
import com.buyology.ecommerce.supplier.domain.Supplier;
import com.buyology.ecommerce.supplier.repository.SupplierRepository;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Supplier-facing, READ-ONLY refunds view: refund requests whose order contains at
 * least one of the current supplier's items. Suppliers can see refunds affecting
 * their products but cannot act on them (approval/payment stays with admins).
 */
@RestController
@RequestMapping("/api/supplier/refunds")
public class SupplierRefundController {

    private final RefundRequestService refundRequestService;
    private final SupplierRepository supplierRepository;

    public SupplierRefundController(RefundRequestService refundRequestService,
                                    SupplierRepository supplierRepository) {
        this.refundRequestService = refundRequestService;
        this.supplierRepository = supplierRepository;
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPPLIER')")
    public ResponseEntity<ApiResponse<Page<RefundRequestResponse>>> myRefunds(
            @RequestParam(required = false) RefundRequestStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Supplier supplier = resolveCurrentSupplier();
        if (supplier == null) {
            return ApiResponse.failure(HttpStatus.FORBIDDEN, "Supplier account not found");
        }
        return ApiResponse.success(
                refundRequestService.listForSupplier(supplier.getId(), status, page, size),
                "Refunds fetched successfully");
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPPLIER')")
    public ResponseEntity<ApiResponse<RefundRequestResponse>> getRefund(@PathVariable UUID id) {
        Supplier supplier = resolveCurrentSupplier();
        if (supplier == null) {
            return ApiResponse.failure(HttpStatus.FORBIDDEN, "Supplier account not found");
        }
        return ApiResponse.success(
                refundRequestService.getForSupplier(id, supplier.getId()),
                "Refund fetched successfully");
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
