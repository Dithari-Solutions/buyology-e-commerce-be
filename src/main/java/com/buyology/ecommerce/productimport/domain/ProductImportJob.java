package com.buyology.ecommerce.productimport.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * One spreadsheet upload, from the file landing to the products existing.
 *
 * <p>The status ladder has two human gates rather than one. Extraction is cheap to redo and
 * expensive to get wrong — a misread spec becomes a customer dispute on a refurbished laptop — so
 * {@code READY_FOR_REVIEW} exists to make sure nothing reaches the catalogue until a person has
 * seen what the sheet was understood to say.
 */
@Entity
@Table(name = "product_import_jobs")
public class ProductImportJob {

    public enum Status {
        /** File stored, rows counted, nothing parsed yet. */
        UPLOADED,
        /** Claude is working through the rows. */
        EXTRACTING,
        /** Every row has been attempted; waiting on a human to approve the import. */
        READY_FOR_REVIEW,
        /** Approved rows are being written to the catalogue. */
        IMPORTING,
        COMPLETED,
        FAILED
    }

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "file_name", nullable = false, length = 400)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private Status status = Status.UPLOADED;

    @Column(name = "total_rows", nullable = false)
    private Integer totalRows = 0;

    @Column(name = "extracted_rows", nullable = false)
    private Integer extractedRows = 0;

    @Column(name = "failed_rows", nullable = false)
    private Integer failedRows = 0;

    @Column(name = "imported_rows", nullable = false)
    private Integer importedRows = 0;

    @Column(name = "default_category_id")
    private UUID defaultCategoryId;

    @Column(name = "default_brand_id")
    private UUID defaultBrandId;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "created_by_admin_id", nullable = false)
    private UUID createdByAdminId;

    @Column(name = "created_by_admin_name", length = 200)
    private String createdByAdminName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

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

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public Integer getTotalRows() { return totalRows; }
    public void setTotalRows(Integer totalRows) { this.totalRows = totalRows; }

    public Integer getExtractedRows() { return extractedRows; }
    public void setExtractedRows(Integer extractedRows) { this.extractedRows = extractedRows; }

    public Integer getFailedRows() { return failedRows; }
    public void setFailedRows(Integer failedRows) { this.failedRows = failedRows; }

    public Integer getImportedRows() { return importedRows; }
    public void setImportedRows(Integer importedRows) { this.importedRows = importedRows; }

    public UUID getDefaultCategoryId() { return defaultCategoryId; }
    public void setDefaultCategoryId(UUID defaultCategoryId) { this.defaultCategoryId = defaultCategoryId; }

    public UUID getDefaultBrandId() { return defaultBrandId; }
    public void setDefaultBrandId(UUID defaultBrandId) { this.defaultBrandId = defaultBrandId; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public UUID getCreatedByAdminId() { return createdByAdminId; }
    public void setCreatedByAdminId(UUID createdByAdminId) { this.createdByAdminId = createdByAdminId; }

    public String getCreatedByAdminName() { return createdByAdminName; }
    public void setCreatedByAdminName(String createdByAdminName) { this.createdByAdminName = createdByAdminName; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
