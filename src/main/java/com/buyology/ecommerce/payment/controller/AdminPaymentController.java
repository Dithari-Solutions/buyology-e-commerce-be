package com.buyology.ecommerce.payment.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.payment.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Admin recovery actions on payments.
 *
 * <p>Exists for one situation: the gateway took the money and our side never recorded it, so a
 * paid order sits in PENDING_PAYMENT where no automatic path can rescue it — the reconciler only
 * promotes orders whose transaction is already SUCCESS. Rather than an admin editing the order
 * status by hand (which leaves the payment record disagreeing with the order and skips the paid
 * side effects), this asks Paymob what actually happened and settles the order through the normal
 * success path if the answer is "paid".
 */
@RestController
@RequestMapping("/api/admin/payments")
public class AdminPaymentController {

    private final PaymentService paymentService;

    public AdminPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Re-check an order's outstanding payment against the gateway and settle it if it was paid.
     * Superadmin only: it can move an order into PAID, which starts fulfilment.
     */
    @PreAuthorize("hasRole('SUPERADMIN') or @rbacPolicy.legacyAdmin()")
    @PostMapping("/orders/{orderId}/recheck")
    public ResponseEntity<ApiResponse<PaymentService.RecheckResult>> recheck(
            @PathVariable UUID orderId,
            @RequestParam(name = "providerTransactionId", required = false) String providerTransactionId) {
        PaymentService.RecheckResult result =
                paymentService.recheckOrderPayment(orderId, providerTransactionId);
        return ApiResponse.success(result, result.message());
    }
}
