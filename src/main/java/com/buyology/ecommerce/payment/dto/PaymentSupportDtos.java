package com.buyology.ecommerce.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** DTOs for the admin's view of a payment that did not complete, and their outreach about it. */
public final class PaymentSupportDtos {

    private PaymentSupportDtos() {}

    /**
     * One payment attempt. Together these are the "they struggled, then they paid" history — no
     * separate audit table needed, because every attempt already writes one of these rows.
     */
    public record PaymentAttempt(
            UUID id,
            String methodType,
            String status,
            BigDecimal amount,
            String currency,
            String failureReason,
            String failureCode,
            Long paymobTransactionId,
            boolean reachedGateway,
            Instant createdAt
    ) {}

    /** A message an admin sent, as it appears in the order's outreach log. */
    public record PaymentMessage(
            UUID id,
            String templateKey,
            String subject,
            String body,
            String diagnosisCode,
            String sentByName,
            boolean emailSent,
            boolean notificationSent,
            Instant createdAt
    ) {}

    /** A ready-made message an admin can pick instead of writing one. */
    public record MessageTemplate(String key, String label, String subject, String body) {}

    /** Everything the order-detail payment panel needs, in one call. */
    public record PaymentSupportView(
            PaymentStallDiagnosis diagnosis,
            List<PaymentAttempt> attempts,
            List<PaymentMessage> messages,
            List<MessageTemplate> templates,
            String customerEmail,
            boolean canContactCustomer,
            String repayUrl
    ) {}

    /** What the admin submits. A template key pre-fills the box; the sent text is always the body. */
    public record SendMessageRequest(
            String templateKey,
            @NotBlank @Size(max = 200) String subject,
            @NotBlank @Size(max = 5000) String body
    ) {}
}
