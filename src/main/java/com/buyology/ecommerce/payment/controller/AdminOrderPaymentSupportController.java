package com.buyology.ecommerce.payment.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.payment.dto.PaymentSupportDtos.PaymentMessage;
import com.buyology.ecommerce.payment.dto.PaymentSupportDtos.PaymentSupportView;
import com.buyology.ecommerce.payment.dto.PaymentSupportDtos.SendMessageRequest;
import com.buyology.ecommerce.payment.service.OrderPaymentSupportService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Why an order's payment did not complete, and the admin's outreach about it.
 *
 * <p>Reading is gated on {@code order:read} like the rest of the order-detail view, since the
 * panel lives inside it. Sending is gated separately on {@code order:payment:contact}: messaging a
 * customer in the store's name is a different act from looking at their order, and a support role
 * granted read access should not acquire it by accident.
 */
@RestController
@RequestMapping("/api/admin/orders/{orderId}/payment-support")
public class AdminOrderPaymentSupportController {

    private final OrderPaymentSupportService service;

    public AdminOrderPaymentSupportController(OrderPaymentSupportService service) {
        this.service = service;
    }

    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('order:read') or @rbacPolicy.legacyAdmin()")
    @GetMapping
    public ResponseEntity<ApiResponse<PaymentSupportView>> get(@PathVariable UUID orderId) {
        return ApiResponse.success(service.getSupportView(orderId), "Payment support fetched");
    }

    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('order:payment:contact') or @rbacPolicy.legacyAdmin()")
    @PostMapping("/messages")
    public ResponseEntity<ApiResponse<PaymentMessage>> send(
            @AuthenticationPrincipal UUID adminId,
            @PathVariable UUID orderId,
            @Valid @RequestBody SendMessageRequest request) {
        return ApiResponse.success(service.sendMessage(orderId, request, adminId),
                "Message sent to the customer");
    }
}
