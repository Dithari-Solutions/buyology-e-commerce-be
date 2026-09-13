package com.buyology.ecommerce.product.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products", uniqueConstraints = {
        @UniqueConstraint(columnNames = "sku")
})
public class Product {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private ProductCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id")
    private Brand brand;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", length = 20)
    private ProductType productType;

    @Column(name = "is_refurbished", nullable = false)
    private Boolean isRefurbished = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "refurb_grade", length = 10)
    private RefurbGrade refurbGrade;

    @Column(name = "sku", nullable = false, unique = true, length = 255)
    private String sku;

    @Enumerated(EnumType.STRING)
    @Column(name = "availability_status", length = 20)
    private AvailabilityStatus availabilityStatus = AvailabilityStatus.PRE_ORDER;

    @Column(name = "is_super_deal", nullable = false)
    private Boolean isSuperDeal = false;

    @Column(name = "is_limited_stock", nullable = false)
    private Boolean isLimitedStock = false;

    // Admin-managed stock count. Null = not tracked. Drives the storefront's
    // low-stock urgency message (shown when 0 < stockQuantity < 5).
    //
    // NOT an inventory count and never has been — see availableQuantity below, and V55.
    @Column(name = "stock_quantity")
    private Integer stockQuantity;

    /**
     * Units on hand, and the number allowed to REFUSE an order.
     *
     * <p>Null means this product's stock is not tracked: it sells without a ceiling, which is how the
     * whole catalogue behaves until somebody states a count. Any number is a claim that we hold that
     * many, and the order path will not sell past it.
     *
     * <p>Distinct from {@link #stockQuantity} deliberately. That one is a display hint that has been
     * decrementing past whatever an admin typed since V12, with nothing ever putting units back, so a
     * product that merely sold well reads 0 — which is why enforcing it refused checkout for the
     * best-selling catalogue and had to be rolled back. This column has no such history: every
     * existing row is null, so enforcement costs nothing until the number is somebody's word.
     *
     * <p>Enforced whatever the {@link AvailabilityStatus}, PRE_ORDER included. stockQuantity's guard
     * exempts PRE_ORDER because "accept orders we cannot fill yet" is an instruction to skip a stock
     * check — but that reasoning does not survive an admin typing an explicit number, and PRE_ORDER is
     * the DEFAULT for a new product, so exempting it here would mean the count silently did nothing on
     * most of the catalogue. If you want a product to sell without a ceiling, leave this null; that is
     * what null is for.
     */
    @Column(name = "available_quantity")
    private Integer availableQuantity;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(name = "is_active")
    private Boolean isActive = true;

    // Bulk-import provenance. needsFulfilment is set by the importer and cleared by a human; it
    // answers a different question from status — status is "may this be sold", this is "has a
    // person checked it and added the photos". importNotes carries what the extraction was unsure
    // about plus the original spreadsheet line, which is what lets the human finish it quickly.
    @Column(name = "needs_fulfilment", nullable = false)
    private Boolean needsFulfilment = false;

    @Column(name = "import_notes", columnDefinition = "TEXT")
    private String importNotes;

    @Column(name = "import_job_id")
    private UUID importJobId;

    // Supplier product fields (null for admin-created products)
    @Column(name = "supplier_id")
    private UUID supplierId;

    @Enumerated(EnumType.STRING)
    @Column(name = "supplier_status", length = 20)
    private SupplierStatus supplierStatus;

    @Column(name = "supplier_rejection_reason", columnDefinition = "TEXT")
    private String supplierRejectionReason;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Enums
    public enum ProductType {
        SIMPLE,
        DIY,
        ACCESSORY
    }

    public enum AvailabilityStatus {
        IN_STOCK,
        OUT_OF_STOCK,
        PRE_ORDER
    }

    public enum RefurbGrade {
        A,
        B,
        C
    }

    // Kept here because StoreProduct references Product.DiscountType
    public enum DiscountType {
        FIXED,
        PERCENTAGE
    }

    public enum SupplierStatus {
        PENDING_REVIEW, APPROVED, REJECTED
    }

    // Constructors
    public Product() {
    }

    public Product(ProductCategory category, Brand brand, ProductType productType, Boolean isRefurbished,
            RefurbGrade refurbGrade, String sku, String status, AvailabilityStatus availabilityStatus,
            Boolean isSuperDeal, Boolean isLimitedStock) {
        this.category = category;
        this.brand = brand;
        this.productType = productType;
        this.isRefurbished = isRefurbished != null ? isRefurbished : false;
        this.refurbGrade = refurbGrade;
        this.sku = sku;
        this.status = status != null ? status : "ACTIVE";
        this.availabilityStatus = availabilityStatus != null ? availabilityStatus : AvailabilityStatus.PRE_ORDER;
        this.isSuperDeal = isSuperDeal != null ? isSuperDeal : false;
        this.isLimitedStock = isLimitedStock != null ? isLimitedStock : false;
    }

    // Lifecycle hooks
    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.isRefurbished == null) this.isRefurbished = false;
        if (this.status == null) this.status = "ACTIVE";
        if (this.availabilityStatus == null) this.availabilityStatus = AvailabilityStatus.PRE_ORDER;
        if (this.isSuperDeal == null) this.isSuperDeal = false;
        if (this.isLimitedStock == null) this.isLimitedStock = false;
        if (this.needsFulfilment == null) this.needsFulfilment = false;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ProductCategory getCategory() {
        return category;
    }

    public void setCategory(ProductCategory category) {
        this.category = category;
    }

    public Brand getBrand() {
        return brand;
    }

    public void setBrand(Brand brand) {
        this.brand = brand;
    }

    public ProductType getProductType() {
        return productType;
    }

    public void setProductType(ProductType productType) {
        this.productType = productType;
    }

    public Boolean getIsRefurbished() {
        return isRefurbished;
    }

    public void setIsRefurbished(Boolean isRefurbished) {
        this.isRefurbished = isRefurbished;
    }

    public RefurbGrade getRefurbGrade() {
        return refurbGrade;
    }

    public void setRefurbGrade(RefurbGrade refurbGrade) {
        this.refurbGrade = refurbGrade;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public AvailabilityStatus getAvailabilityStatus() {
        return availabilityStatus;
    }

    public void setAvailabilityStatus(AvailabilityStatus availabilityStatus) {
        this.availabilityStatus = availabilityStatus;
    }

    public Boolean getIsSuperDeal() {
        return isSuperDeal;
    }

    public void setIsSuperDeal(Boolean isSuperDeal) {
        this.isSuperDeal = isSuperDeal;
    }

    public Boolean getIsLimitedStock() {
        return isLimitedStock;
    }

    public void setIsLimitedStock(Boolean isLimitedStock) {
        this.isLimitedStock = isLimitedStock;
    }

    public Integer getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(Integer stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public Integer getAvailableQuantity() {
        return availableQuantity;
    }

    public void setAvailableQuantity(Integer availableQuantity) {
        this.availableQuantity = availableQuantity;
    }

    /** Whether this product has a stated count, and therefore a ceiling an order cannot exceed. */
    public boolean tracksAvailableQuantity() {
        return availableQuantity != null;
    }

    public UUID getSupplierId() { return supplierId; }
    public void setSupplierId(UUID supplierId) { this.supplierId = supplierId; }

    public SupplierStatus getSupplierStatus() { return supplierStatus; }
    public void setSupplierStatus(SupplierStatus supplierStatus) { this.supplierStatus = supplierStatus; }

    public String getSupplierRejectionReason() { return supplierRejectionReason; }
    public void setSupplierRejectionReason(String supplierRejectionReason) { this.supplierRejectionReason = supplierRejectionReason; }

    public Boolean getNeedsFulfilment() { return needsFulfilment; }
    public void setNeedsFulfilment(Boolean needsFulfilment) { this.needsFulfilment = needsFulfilment; }

    public String getImportNotes() { return importNotes; }
    public void setImportNotes(String importNotes) { this.importNotes = importNotes; }

    public UUID getImportJobId() { return importJobId; }
    public void setImportJobId(UUID importJobId) { this.importJobId = importJobId; }
}
