package com.buyology.ecommerce.refund.domain;

import com.buyology.ecommerce.refund.enums.RefundRequestStatus;
import com.buyology.ecommerce.refund.enums.RefundReturnMethod;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Customer-initiated refund workflow. Distinct from {@code PaymentRefund}, which only
 * records the actual money-refund event when the admin pays. This entity captures the
 * full request lifecycle: description, ≥3 product images, admin review, return method,
 * staff inspection, and final settlement.
 */
@Entity
@Table(name = "refund_requests", indexes = {
        @Index(name = "idx_refund_requests_user", columnList = "user_id"),
        @Index(name = "idx_refund_requests_order", columnList = "order_id"),
        @Index(name = "idx_refund_requests_status", columnList = "status")
})
public class RefundRequest {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Snapshot of the order's payment transaction at request time — used for the Paymob refund call. */
    @Column(name = "payment_transaction_id", nullable = false)
    private UUID paymentTransactionId;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @ElementCollection
    @CollectionTable(
            name = "refund_request_images",
            joinColumns = @JoinColumn(name = "refund_request_id"),
            indexes = @Index(name = "idx_refund_request_images_req", columnList = "refund_request_id")
    )
    @Column(name = "image_key", length = 500, nullable = false)
    private List<String> imageKeys = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private RefundRequestStatus status = RefundRequestStatus.PENDING_REVIEW;

    @Enumerated(EnumType.STRING)
    @Column(name = "return_method", length = 30)
    private RefundReturnMethod returnMethod;

    /** Snapshot of the courier fee in the customer's chosen currency, taken at the time the customer picks COURIER_PICKUP. */
    @Column(name = "courier_fee_amount", precision = 12, scale = 2)
    private BigDecimal courierFeeAmount;

    @Column(name = "courier_fee_currency", length = 3)
    private String courierFeeCurrency;

    /** Snapshot of the order total at request time — what we will refund through Paymob. */
    @Column(name = "refund_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "refund_currency", nullable = false, length = 3)
    private String refundCurrency;

    /** FK to {@code PaymentRefund.id} once the admin has paid. */
    @Column(name = "payment_refund_id")
    private UUID paymentRefundId;

    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "paid_by")
    private UUID paidBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) this.status = RefundRequestStatus.PENDING_REVIEW;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getPaymentTransactionId() { return paymentTransactionId; }
    public void setPaymentTransactionId(UUID paymentTransactionId) { this.paymentTransactionId = paymentTransactionId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<String> getImageKeys() { return imageKeys; }
    public void setImageKeys(List<String> imageKeys) { this.imageKeys = imageKeys; }

    public RefundRequestStatus getStatus() { return status; }
    public void setStatus(RefundRequestStatus status) { this.status = status; }

    public RefundReturnMethod getReturnMethod() { return returnMethod; }
    public void setReturnMethod(RefundReturnMethod returnMethod) { this.returnMethod = returnMethod; }

    public BigDecimal getCourierFeeAmount() { return courierFeeAmount; }
    public void setCourierFeeAmount(BigDecimal courierFeeAmount) { this.courierFeeAmount = courierFeeAmount; }

    public String getCourierFeeCurrency() { return courierFeeCurrency; }
    public void setCourierFeeCurrency(String courierFeeCurrency) { this.courierFeeCurrency = courierFeeCurrency; }

    public BigDecimal getRefundAmount() { return refundAmount; }
    public void setRefundAmount(BigDecimal refundAmount) { this.refundAmount = refundAmount; }

    public String getRefundCurrency() { return refundCurrency; }
    public void setRefundCurrency(String refundCurrency) { this.refundCurrency = refundCurrency; }

    public UUID getPaymentRefundId() { return paymentRefundId; }
    public void setPaymentRefundId(UUID paymentRefundId) { this.paymentRefundId = paymentRefundId; }

    public String getAdminNote() { return adminNote; }
    public void setAdminNote(String adminNote) { this.adminNote = adminNote; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public UUID getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(UUID reviewedBy) { this.reviewedBy = reviewedBy; }

    public UUID getPaidBy() { return paidBy; }
    public void setPaidBy(UUID paidBy) { this.paidBy = paidBy; }

    public Instant getApprovedAt() { return approvedAt; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }

    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }

    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
