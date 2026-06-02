package com.buyology.ecommerce.revenue.enums;

/** File format produced by a revenue export. */
public enum RevenueExportFormat {
    CSV("text/csv", "csv"),
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx");

    private final String contentType;
    private final String extension;

    RevenueExportFormat(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String getContentType() {
        return contentType;
    }

    public String getExtension() {
        return extension;
    }
}
