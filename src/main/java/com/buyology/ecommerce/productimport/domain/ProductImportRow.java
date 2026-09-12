package com.buyology.ecommerce.productimport.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One line of the spreadsheet: what the supplier wrote, and what it was understood to mean.
 *
 * <p>{@link #rawText} is kept verbatim and never normalised. When an extraction turns out to be
 * wrong — and on a sheet where the same laptop is written three different ways, some will be — this
 * is the only surviving record of what the supplier actually said.
 */
@Entity
@Table(name = "product_import_rows")
public class ProductImportRow {

    public enum Status {
        PENDING,
        EXTRACTED,
        EXTRACTION_FAILED,
        IMPORTED,
        IMPORT_FAILED,
        /** Deselected by the reviewer, or a row the sheet did not really describe a product on. */
        SKIPPED
    }

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "row_number", nullable = false)
    private Integer rowNumber;

    @Column(name = "raw_code", length = 200)
    private String rawCode;

    @Column(name = "raw_text", nullable = false, columnDefinition = "TEXT")
    private String rawText;

    @Column(name = "raw_quantity")
    private Integer rawQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private Status status = Status.PENDING;

    @Column(name = "brand", length = 120)
    private String brand;

    @Column(name = "model", length = 200)
    private String model;

    @Column(name = "device_type", length = 40)
    private String deviceType;

    @Column(name = "processor", length = 120)
    private String processor;

    @Column(name = "processor_gen")
    private Integer processorGen;

    @Column(name = "ram_gb")
    private Integer ramGb;

    @Column(name = "storage_gb")
    private Integer storageGb;

    @Column(name = "storage_type", length = 20)
    private String storageType;

    @Column(name = "screen_inches", precision = 5, scale = 2)
    private BigDecimal screenInches;

    @Column(name = "operating_system", length = 80)
    private String operatingSystem;

    @Column(name = "gpu_gb")
    private Integer gpuGb;

    @Column(name = "touchscreen")
    private Boolean touchscreen;

    @Column(name = "colour", length = 60)
    private String colour;

    @Column(name = "title_en", length = 255)
    private String titleEn;

    @Column(name = "title_az", length = 255)
    private String titleAz;

    @Column(name = "title_ar", length = 255)
    private String titleAr;

    @Column(name = "description_en", columnDefinition = "TEXT")
    private String descriptionEn;

    @Column(name = "description_az", columnDefinition = "TEXT")
    private String descriptionAz;

    @Column(name = "description_ar", columnDefinition = "TEXT")
    private String descriptionAr;

    @Column(name = "confidence", length = 20)
    private String confidence;

    /** One short phrase per line — what a human has to settle before this can be published. */
    @Column(name = "needs_review", columnDefinition = "TEXT")
    private String needsReview;

    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getJobId() { return jobId; }
    public void setJobId(UUID jobId) { this.jobId = jobId; }

    public Integer getRowNumber() { return rowNumber; }
    public void setRowNumber(Integer rowNumber) { this.rowNumber = rowNumber; }

    public String getRawCode() { return rawCode; }
    public void setRawCode(String rawCode) { this.rawCode = rawCode; }

    public String getRawText() { return rawText; }
    public void setRawText(String rawText) { this.rawText = rawText; }

    public Integer getRawQuantity() { return rawQuantity; }
    public void setRawQuantity(Integer rawQuantity) { this.rawQuantity = rawQuantity; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }

    public String getProcessor() { return processor; }
    public void setProcessor(String processor) { this.processor = processor; }

    public Integer getProcessorGen() { return processorGen; }
    public void setProcessorGen(Integer processorGen) { this.processorGen = processorGen; }

    public Integer getRamGb() { return ramGb; }
    public void setRamGb(Integer ramGb) { this.ramGb = ramGb; }

    public Integer getStorageGb() { return storageGb; }
    public void setStorageGb(Integer storageGb) { this.storageGb = storageGb; }

    public String getStorageType() { return storageType; }
    public void setStorageType(String storageType) { this.storageType = storageType; }

    public BigDecimal getScreenInches() { return screenInches; }
    public void setScreenInches(BigDecimal screenInches) { this.screenInches = screenInches; }

    public String getOperatingSystem() { return operatingSystem; }
    public void setOperatingSystem(String operatingSystem) { this.operatingSystem = operatingSystem; }

    public Integer getGpuGb() { return gpuGb; }
    public void setGpuGb(Integer gpuGb) { this.gpuGb = gpuGb; }

    public Boolean getTouchscreen() { return touchscreen; }
    public void setTouchscreen(Boolean touchscreen) { this.touchscreen = touchscreen; }

    public String getColour() { return colour; }
    public void setColour(String colour) { this.colour = colour; }

    public String getTitleEn() { return titleEn; }
    public void setTitleEn(String titleEn) { this.titleEn = titleEn; }

    public String getTitleAz() { return titleAz; }
    public void setTitleAz(String titleAz) { this.titleAz = titleAz; }

    public String getTitleAr() { return titleAr; }
    public void setTitleAr(String titleAr) { this.titleAr = titleAr; }

    public String getDescriptionEn() { return descriptionEn; }
    public void setDescriptionEn(String descriptionEn) { this.descriptionEn = descriptionEn; }

    public String getDescriptionAz() { return descriptionAz; }
    public void setDescriptionAz(String descriptionAz) { this.descriptionAz = descriptionAz; }

    public String getDescriptionAr() { return descriptionAr; }
    public void setDescriptionAr(String descriptionAr) { this.descriptionAr = descriptionAr; }

    public String getConfidence() { return confidence; }
    public void setConfidence(String confidence) { this.confidence = confidence; }

    public String getNeedsReview() { return needsReview; }
    public void setNeedsReview(String needsReview) { this.needsReview = needsReview; }

    public UUID getProductId() { return productId; }
    public void setProductId(UUID productId) { this.productId = productId; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
