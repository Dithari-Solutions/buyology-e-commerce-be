package com.buyology.ecommerce.revenue.domain;

import com.buyology.ecommerce.revenue.enums.RevenueExportFormat;
import com.buyology.ecommerce.revenue.enums.RevenueExportType;
import com.buyology.ecommerce.revenue.enums.RevenuePeriod;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Audit record of a generated revenue export. The file itself lives in Contabo
 * object storage at {@link #objectKey}; this row records what was exported and
 * who exported it so the super-admin export-history view can show provenance.
 */
@Entity
@Table(name = "revenue_exports", indexes = {
        @Index(name = "idx_revenue_exports_created", columnList = "created_at"),
        @Index(name = "idx_revenue_exports_actor", columnList = "exported_by_user_id")
})
public class RevenueExport {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "export_type", nullable = false, length = 32)
    private RevenueExportType exportType;

    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false, length = 16)
    private RevenueExportFormat format;

    @Enumerated(EnumType.STRING)
    @Column(name = "period", nullable = false, length = 16)
    private RevenuePeriod period;

    @Column(name = "from_date", nullable = false)
    private LocalDate fromDate;

    @Column(name = "to_date", nullable = false)
    private LocalDate toDate;

    /** Set only for {@code SUPPLIER}-scoped exports. */
    @Column(name = "supplier_id")
    private UUID supplierId;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "exported_by_user_id")
    private UUID exportedByUserId;

    @Column(name = "exported_by_email", length = 255)
    private String exportedByEmail;

    @Column(name = "exported_by_role", length = 64)
    private String exportedByRole;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }

    public RevenueExportType getExportType() { return exportType; }
    public void setExportType(RevenueExportType exportType) { this.exportType = exportType; }

    public RevenueExportFormat getFormat() { return format; }
    public void setFormat(RevenueExportFormat format) { this.format = format; }

    public RevenuePeriod getPeriod() { return period; }
    public void setPeriod(RevenuePeriod period) { this.period = period; }

    public LocalDate getFromDate() { return fromDate; }
    public void setFromDate(LocalDate fromDate) { this.fromDate = fromDate; }

    public LocalDate getToDate() { return toDate; }
    public void setToDate(LocalDate toDate) { this.toDate = toDate; }

    public UUID getSupplierId() { return supplierId; }
    public void setSupplierId(UUID supplierId) { this.supplierId = supplierId; }

    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public UUID getExportedByUserId() { return exportedByUserId; }
    public void setExportedByUserId(UUID exportedByUserId) { this.exportedByUserId = exportedByUserId; }

    public String getExportedByEmail() { return exportedByEmail; }
    public void setExportedByEmail(String exportedByEmail) { this.exportedByEmail = exportedByEmail; }

    public String getExportedByRole() { return exportedByRole; }
    public void setExportedByRole(String exportedByRole) { this.exportedByRole = exportedByRole; }

    public Instant getCreatedAt() { return createdAt; }
}
