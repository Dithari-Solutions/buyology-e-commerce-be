package com.buyology.ecommerce.revenue.service;

import com.buyology.ecommerce.auth.domain.AuthCredentials;
import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import com.buyology.ecommerce.infrastructure.external.ContaboObjectService;
import com.buyology.ecommerce.revenue.domain.RevenueExport;
import com.buyology.ecommerce.revenue.dto.RevenueExportResponse;
import com.buyology.ecommerce.revenue.dto.RevenueReportResponse;
import com.buyology.ecommerce.revenue.dto.SupplierRevenueOverviewResponse;
import com.buyology.ecommerce.revenue.enums.RevenueExportFormat;
import com.buyology.ecommerce.revenue.enums.RevenueExportType;
import com.buyology.ecommerce.revenue.enums.RevenuePeriod;
import com.buyology.ecommerce.revenue.repository.RevenueExportRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Generates revenue export files (CSV / XLSX), archives them in Contabo object
 * storage, and records who exported what in {@link RevenueExport}.
 */
@Service
public class RevenueExportService {

    private final RevenueService revenueService;
    private final RevenueExportRepository exportRepository;
    private final ContaboObjectService contaboObjectService;
    private final AuthCredentialRepository authCredentialRepository;

    public RevenueExportService(
            RevenueService revenueService,
            RevenueExportRepository exportRepository,
            ContaboObjectService contaboObjectService,
            AuthCredentialRepository authCredentialRepository) {
        this.revenueService = revenueService;
        this.exportRepository = exportRepository;
        this.contaboObjectService = contaboObjectService;
        this.authCredentialRepository = authCredentialRepository;
    }

    // ── public API ───────────────────────────────────────────────────────────

    public RevenueExportResponse exportPlatform(RevenueExportFormat format, RevenuePeriod period,
                                                LocalDate from, LocalDate to) {
        RevenueReportResponse report = revenueService.platformReport(period, from, to);
        Tabular data = reportToTabular(report, "Period");
        return generateAndStore(RevenueExportType.PLATFORM, format, report.period(),
                report.from(), report.to(), null, "platform-revenue", data);
    }

    public RevenueExportResponse exportSupplierOverview(RevenueExportFormat format, RevenuePeriod period,
                                                        LocalDate from, LocalDate to) {
        SupplierRevenueOverviewResponse overview = revenueService.supplierOverview(period, from, to);
        Tabular data = new Tabular(
                List.of("Supplier", "Orders", "Gross Revenue", "Refunds", "Net Revenue"), new ArrayList<>());
        overview.suppliers().forEach(s -> data.rows().add(new String[]{
                s.businessName(), String.valueOf(s.orders()), String.valueOf(s.revenue()),
                String.valueOf(s.refunded()), String.valueOf(s.net())}));
        data.rows().add(new String[]{"TOTAL", String.valueOf(overview.totalOrders()),
                String.valueOf(overview.totalRevenue()), String.valueOf(overview.totalRefunded()),
                String.valueOf(overview.netRevenue())});
        return generateAndStore(RevenueExportType.SUPPLIER_ALL, format, overview.period(),
                overview.from(), overview.to(), null, "supplier-revenue-all", data);
    }

    public RevenueExportResponse exportSupplier(UUID supplierId, RevenueExportFormat format, RevenuePeriod period,
                                                LocalDate from, LocalDate to) {
        RevenueReportResponse report = revenueService.supplierReport(supplierId, period, from, to);
        Tabular data = reportToTabular(report, "Period");
        String base = "supplier-revenue-" + supplierId;
        return generateAndStore(RevenueExportType.SUPPLIER, format, report.period(),
                report.from(), report.to(), supplierId, base, data);
    }

    public List<RevenueExportResponse> listExports() {
        return exportRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    // ── generation + storage ───────────────────────────────────────────────────

    private RevenueExportResponse generateAndStore(
            RevenueExportType type, RevenueExportFormat format, RevenuePeriod period,
            LocalDate from, LocalDate to, UUID supplierId, String baseName, Tabular data) {

        byte[] bytes = format == RevenueExportFormat.CSV ? toCsv(data) : toXlsx(data, baseName);
        String fileName = baseName + "_" + period + "_" + from + "_" + to + "." + format.getExtension();
        String year = String.valueOf(LocalDate.now(ZoneOffset.UTC).getYear());
        String objectKey = "revenue-exports/" + type.name().toLowerCase() + "/" + year + "/"
                + UUID.randomUUID() + "-" + fileName;

        contaboObjectService.uploadBytes(objectKey, bytes, format.getContentType());

        UUID userId = currentUserId();
        RevenueExport export = new RevenueExport();
        export.setExportType(type);
        export.setFormat(format);
        export.setPeriod(period);
        export.setFromDate(from);
        export.setToDate(to);
        export.setSupplierId(supplierId);
        export.setObjectKey(objectKey);
        export.setFileName(fileName);
        export.setFileSize(bytes.length);
        export.setExportedByUserId(userId);
        export.setExportedByEmail(currentEmail(userId));
        export.setExportedByRole(currentRole());
        RevenueExport saved = exportRepository.save(export);

        return toResponse(saved);
    }

    private RevenueExportResponse toResponse(RevenueExport e) {
        return new RevenueExportResponse(
                e.getId(), e.getExportType(), e.getFormat(), e.getPeriod(),
                e.getFromDate(), e.getToDate(), e.getSupplierId(), e.getFileName(), e.getFileSize(),
                e.getExportedByUserId(), e.getExportedByEmail(), e.getExportedByRole(),
                e.getCreatedAt(), contaboObjectService.getPresignedUrl(e.getObjectKey()));
    }

    private Tabular reportToTabular(RevenueReportResponse report, String periodHeader) {
        Tabular data = new Tabular(
                List.of(periodHeader, "Orders", "Gross Revenue", "Refunds", "Net Revenue"), new ArrayList<>());
        report.buckets().forEach(b -> data.rows().add(new String[]{
                b.period(), String.valueOf(b.orders()), String.valueOf(b.revenue()),
                String.valueOf(b.refunded()), String.valueOf(b.net())}));
        data.rows().add(new String[]{"TOTAL", String.valueOf(report.totalOrders()),
                String.valueOf(report.totalRevenue()), String.valueOf(report.totalRefunded()),
                String.valueOf(report.netRevenue())});
        return data;
    }

    // ── file writers ───────────────────────────────────────────────────────────

    private byte[] toCsv(Tabular data) {
        StringBuilder sb = new StringBuilder();
        sb.append(data.headers().stream().map(this::csvCell).reduce((a, b) -> a + "," + b).orElse(""));
        sb.append("\r\n");
        for (String[] row : data.rows()) {
            for (int i = 0; i < row.length; i++) {
                if (i > 0) sb.append(',');
                sb.append(csvCell(row[i]));
            }
            sb.append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String csvCell(String value) {
        String v = value == null ? "" : value;
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private byte[] toXlsx(Tabular data, String sheetName) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName.length() > 31 ? sheetName.substring(0, 31) : sheetName);

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            Row header = sheet.createRow(0);
            for (int c = 0; c < data.headers().size(); c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(data.headers().get(c));
                cell.setCellStyle(headerStyle);
            }

            int r = 1;
            for (String[] row : data.rows()) {
                Row sheetRow = sheet.createRow(r++);
                for (int c = 0; c < row.length; c++) {
                    sheetRow.createCell(c).setCellValue(row[c] == null ? "" : row[c]);
                }
            }
            for (int c = 0; c < data.headers().size(); c++) {
                sheet.autoSizeColumn(c);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to build XLSX revenue export", e);
        }
    }

    // ── current actor resolution ────────────────────────────────────────────────

    private UUID currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UUID uid) return uid;
        return null;
    }

    private String currentEmail(UUID userId) {
        if (userId == null) return null;
        return authCredentialRepository.findByUserId(userId).stream()
                .map(AuthCredentials::getEmail)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private String currentRole() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        List<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .toList();
        if (roles.contains("SUPERADMIN")) return "SUPERADMIN";
        if (roles.contains("ADMIN")) return "ADMIN";
        if (roles.contains("SUPPLIER")) return "SUPPLIER";
        return roles.isEmpty() ? null : roles.get(0);
    }

    /** Simple tabular payload used by both the CSV and XLSX writers. */
    private record Tabular(List<String> headers, List<String[]> rows) {
    }
}
