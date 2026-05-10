package com.buyology.ecommerce.refund.dto;

/**
 * Generic request body shared by approve / reject. {@code adminNote} is used when approving;
 * {@code rejectionReason} when rejecting.
 */
public record AdminRefundActionRequest(
        String adminNote,
        String rejectionReason
) {}
