package com.buyology.ecommerce.refund.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Singleton configuration row for the refund module. Inserted on first read with defaults
 * if the table is empty. Admin edits the window (in days) and the courier base fee (AED).
 */
@Entity
@Table(name = "refund_settings")
public class RefundSetting {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "refund_window_days", nullable = false)
    private Integer refundWindowDays;

    @Column(name = "courier_fee_aed", nullable = false, precision = 12, scale = 2)
    private BigDecimal courierFeeAed;

    @Column(name = "courier_fee_currency", nullable = false, length = 3)
    private String courierFeeCurrency = "AED";

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.courierFeeCurrency == null) this.courierFeeCurrency = "AED";
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Integer getRefundWindowDays() { return refundWindowDays; }
    public void setRefundWindowDays(Integer refundWindowDays) { this.refundWindowDays = refundWindowDays; }

    public BigDecimal getCourierFeeAed() { return courierFeeAed; }
    public void setCourierFeeAed(BigDecimal courierFeeAed) { this.courierFeeAed = courierFeeAed; }

    public String getCourierFeeCurrency() { return courierFeeCurrency; }
    public void setCourierFeeCurrency(String courierFeeCurrency) { this.courierFeeCurrency = courierFeeCurrency; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
