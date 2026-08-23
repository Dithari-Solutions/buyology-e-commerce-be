package com.buyology.ecommerce.order.dto;

import com.buyology.ecommerce.order.domain.enums.DeliveryMethod;
import com.buyology.ecommerce.order.domain.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class OrderAdminResponse extends OrderResponse {

    private String pickupProofImageUrl;
    private Instant pickupProofTakenAt;
    private String deliveryProofImageUrl;
    private String deliveryProofSignatureUrl;
    private String deliveredTo;
    private Instant deliveryProofTakenAt;
    private UUID storeId;

    // ── Payment identity (admin-only) ─────────────────────────────────────────
    /** CARD / TABBY / TAMARA / B2B_CREDIT, from the settling transaction. */
    private String paymentMethodType;
    /** Masked card tail, when Paymob reported it. Never anything close to a full PAN. */
    private String cardLast4;
    private String cardBrand;

    // ── Quiqup delivery dispatch ──────────────────────────────────────────────
    // Admin-only, deliberately: an operator needs to know whether an order reached the carrier and
    // why it did not, and a customer needs neither. Without these the dispatch state lived only on
    // the entity, so the one question an operator actually has — "did this order reach Quiqup?" —
    // was answerable only by opening a database console.

    /** Quiqup's own job id, once one exists. Null means this order was never dispatched. */
    private String quiqupOrderId;

    /** The last delivery status Quiqup reported, in their wording. */
    private String quiqupStatus;

    private Instant quiqupDispatchedAt;

    /**
     * Why the last dispatch attempt failed, or null after a success.
     *
     * <p>This is the field that turns a stuck order into an actionable one: a multi-store order or
     * one missing coordinates is refused on purpose and says so here, and nothing else in the
     * system reports it.
     */
    private String quiqupDispatchError;

    /**
     * How stopping the Quiqup job went when this order was cancelled: CONFIRMED,
     * CONFIRMED_BY_PARTNER, PENDING, UNCONFIRMED, REFUSED_TOO_LATE, NEEDS_HUMAN. Null means the
     * question never arose. REFUSED_TOO_LATE and NEEDS_HUMAN are the rows a human must act on —
     * the refund is withheld until they do.
     */
    private String quiqupCancelStatus;
    private java.time.Instant quiqupCancelConfirmedAt;
    private String quiqupCancelError;
    /** When the cancellation refund was handed to the gateway. Null on a cancelled order = held. */
    private java.time.Instant cancelRefundInitiatedAt;

    // Getters and Setters

    public String getPickupProofImageUrl() { return pickupProofImageUrl; }
    public void setPickupProofImageUrl(String pickupProofImageUrl) { this.pickupProofImageUrl = pickupProofImageUrl; }

    public Instant getPickupProofTakenAt() { return pickupProofTakenAt; }
    public void setPickupProofTakenAt(Instant pickupProofTakenAt) { this.pickupProofTakenAt = pickupProofTakenAt; }

    public String getDeliveryProofImageUrl() { return deliveryProofImageUrl; }
    public void setDeliveryProofImageUrl(String deliveryProofImageUrl) { this.deliveryProofImageUrl = deliveryProofImageUrl; }

    public String getDeliveryProofSignatureUrl() { return deliveryProofSignatureUrl; }
    public void setDeliveryProofSignatureUrl(String deliveryProofSignatureUrl) { this.deliveryProofSignatureUrl = deliveryProofSignatureUrl; }

    public String getDeliveredTo() { return deliveredTo; }
    public void setDeliveredTo(String deliveredTo) { this.deliveredTo = deliveredTo; }

    public Instant getDeliveryProofTakenAt() { return deliveryProofTakenAt; }
    public void setDeliveryProofTakenAt(Instant deliveryProofTakenAt) { this.deliveryProofTakenAt = deliveryProofTakenAt; }

    public UUID getStoreId() { return storeId; }
    public void setStoreId(UUID storeId) { this.storeId = storeId; }
    public String getPaymentMethodType() { return paymentMethodType; }
    public void setPaymentMethodType(String paymentMethodType) { this.paymentMethodType = paymentMethodType; }
    public String getCardLast4() { return cardLast4; }
    public void setCardLast4(String cardLast4) { this.cardLast4 = cardLast4; }
    public String getCardBrand() { return cardBrand; }
    public void setCardBrand(String cardBrand) { this.cardBrand = cardBrand; }

    public String getQuiqupCancelStatus() { return quiqupCancelStatus; }
    public void setQuiqupCancelStatus(String v) { this.quiqupCancelStatus = v; }
    public java.time.Instant getQuiqupCancelConfirmedAt() { return quiqupCancelConfirmedAt; }
    public void setQuiqupCancelConfirmedAt(java.time.Instant v) { this.quiqupCancelConfirmedAt = v; }
    public String getQuiqupCancelError() { return quiqupCancelError; }
    public void setQuiqupCancelError(String v) { this.quiqupCancelError = v; }
    public java.time.Instant getCancelRefundInitiatedAt() { return cancelRefundInitiatedAt; }
    public void setCancelRefundInitiatedAt(java.time.Instant v) { this.cancelRefundInitiatedAt = v; }

    public String getQuiqupOrderId() { return quiqupOrderId; }
    public void setQuiqupOrderId(String quiqupOrderId) { this.quiqupOrderId = quiqupOrderId; }

    public String getQuiqupStatus() { return quiqupStatus; }
    public void setQuiqupStatus(String quiqupStatus) { this.quiqupStatus = quiqupStatus; }

    public Instant getQuiqupDispatchedAt() { return quiqupDispatchedAt; }
    public void setQuiqupDispatchedAt(Instant v) { this.quiqupDispatchedAt = v; }

    public String getQuiqupDispatchError() { return quiqupDispatchError; }
    public void setQuiqupDispatchError(String v) { this.quiqupDispatchError = v; }
}
