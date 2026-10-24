package com.buyology.ecommerce.warranty.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The warranty for one unit sold. Customers look it up by {@link #serialNumber} on Dithari's site;
 * the Buyology storefront only shows the serial.
 *
 * <p>Order, item and product are plain UUID columns, as elsewhere across modules. The product
 * fields below are a snapshot taken when the card was issued, so later edits to the listing do not
 * rewrite what the customer was sold.
 */
@Entity
@Table(name = "warranty_cards", uniqueConstraints = {
        @UniqueConstraint(name = "uq_warranty_cards_serial", columnNames = "serial_number"),
        @UniqueConstraint(name = "uq_warranty_cards_unit", columnNames = {"order_item_id", "unit_index"})
})
public class WarrantyCard {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "serial_number", nullable = false, length = 32)
    private String serialNumber;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "order_item_id", nullable = false)
    private UUID orderItemId;

    /** 1-based position of this unit within its order item's quantity. */
    @Column(name = "unit_index", nullable = false)
    private int unitIndex;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "variant_id")
    private UUID variantId;

    @Column(name = "duration_months", nullable = false)
    private int durationMonths;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    /** Last day of cover, inclusive. */
    @Column(name = "ends_on", nullable = false)
    private LocalDate endsOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private WarrantyCardStatus status = WarrantyCardStatus.ACTIVE;

    @Column(name = "void_reason", length = 255)
    private String voidReason;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "voided_by")
    private UUID voidedBy;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(name = "brand_name", length = 120)
    private String brandName;

    @Column(name = "category_name", length = 255)
    private String categoryName;

    /** "New", or "Refurbished · Grade A". */
    @Column(name = "condition_label", length = 60)
    private String conditionLabel;

    /** JSON array of {label, value}, the product's main specs in display order. */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "specs", columnDefinition = "jsonb")
    private String specs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) this.status = WarrantyCardStatus.ACTIVE;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }

    public UUID getOrderItemId() { return orderItemId; }
    public void setOrderItemId(UUID orderItemId) { this.orderItemId = orderItemId; }

    public int getUnitIndex() { return unitIndex; }
    public void setUnitIndex(int unitIndex) { this.unitIndex = unitIndex; }

    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }

    public UUID getVariantId() { return variantId; }
    public void setVariantId(UUID variantId) { this.variantId = variantId; }

    public int getDurationMonths() { return durationMonths; }
    public void setDurationMonths(int durationMonths) { this.durationMonths = durationMonths; }

    public LocalDate getStartsOn() { return startsOn; }
    public void setStartsOn(LocalDate startsOn) { this.startsOn = startsOn; }

    public LocalDate getEndsOn() { return endsOn; }
    public void setEndsOn(LocalDate endsOn) { this.endsOn = endsOn; }

    public WarrantyCardStatus getStatus() { return status; }
    public void setStatus(WarrantyCardStatus status) { this.status = status; }

    public String getVoidReason() { return voidReason; }
    public void setVoidReason(String voidReason) { this.voidReason = voidReason; }

    public Instant getVoidedAt() { return voidedAt; }
    public void setVoidedAt(Instant voidedAt) { this.voidedAt = voidedAt; }

    public UUID getVoidedBy() { return voidedBy; }
    public void setVoidedBy(UUID voidedBy) { this.voidedBy = voidedBy; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getBrandName() { return brandName; }
    public void setBrandName(String brandName) { this.brandName = brandName; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public String getConditionLabel() { return conditionLabel; }
    public void setConditionLabel(String conditionLabel) { this.conditionLabel = conditionLabel; }

    public String getSpecs() { return specs; }
    public void setSpecs(String specs) { this.specs = specs; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
