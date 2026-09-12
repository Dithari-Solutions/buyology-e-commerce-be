package com.buyology.ecommerce.productimport.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.productimport.domain.ProductImportJob;
import com.buyology.ecommerce.productimport.dto.FulfilmentQueueItem;
import com.buyology.ecommerce.productimport.dto.ImportJobResponse;
import com.buyology.ecommerce.productimport.dto.ImportRowResponse;
import com.buyology.ecommerce.productimport.service.ProductImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Bulk product import from a supplier spreadsheet.
 *
 * <p>Four steps, and the third is a person: upload the sheet, let Claude read it, review what it
 * was understood to say, then import the rows that are right. The review step is not ceremony —
 * the rows it is protecting against are the ones that parse cleanly into the wrong machine.
 */
@RestController
@RequestMapping("/api/admin/product-imports")
@Tag(name = "Admin — Product import")
public class ProductImportController {

    private final ProductImportService service;

    public ProductImportController(ProductImportService service) {
        this.service = service;
    }

    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('product:create') or @rbacPolicy.legacyAdmin()")
    @Operation(summary = "Upload a spreadsheet and start reading it")
    public ResponseEntity<ApiResponse<ImportJobResponse>> upload(
            @AuthenticationPrincipal UUID adminId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("categoryId") UUID categoryId,
            @RequestParam(value = "brandId", required = false) UUID brandId,
            @RequestParam(value = "adminName", required = false) String adminName) {

        ProductImportJob job = service.createJob(file, categoryId, brandId, adminId, adminName);

        // Kicked off after createJob's transaction has committed — the async worker reads the job
        // and its rows from the database, and would find neither if it started first.
        service.runExtraction(job.getId());

        return ApiResponse.created(ImportJobResponse.from(job),
                "Reading " + job.getTotalRows() + " row(s). This takes a minute.");
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('product:read') or @rbacPolicy.legacyAdmin()")
    @Operation(summary = "Recent imports")
    public ResponseEntity<ApiResponse<List<ImportJobResponse>>> list() {
        return ApiResponse.success(
                service.listJobs().stream().map(ImportJobResponse::from).toList(),
                "Imports fetched");
    }

    @GetMapping("/fulfilment-queue")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('product:read') or @rbacPolicy.legacyAdmin()")
    @Operation(summary = "Imported products still waiting on photos and a final check")
    public ResponseEntity<ApiResponse<List<FulfilmentQueueItem>>> fulfilmentQueue() {
        return ApiResponse.success(
                service.getFulfilmentQueue().stream().map(FulfilmentQueueItem::from).toList(),
                "Fulfilment queue fetched");
    }

    // Declared after the literal route above on purpose: {jobId} binds a UUID, and
    // "fulfilment-queue" is not one, so matcher precedence is not something to leave to chance.
    @GetMapping("/{jobId}")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('product:read') or @rbacPolicy.legacyAdmin()")
    @Operation(summary = "One import, with every row and what was read from it")
    public ResponseEntity<ApiResponse<Map<String, Object>>> get(@PathVariable UUID jobId) {
        ProductImportJob job = service.getJob(jobId);
        List<ImportRowResponse> rows = service.getRows(jobId).stream()
                .map(ImportRowResponse::from)
                .toList();
        return ApiResponse.success(
                Map.of("job", ImportJobResponse.from(job), "rows", rows),
                "Import fetched");
    }

    @PostMapping("/{jobId}/import")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('product:create') or @rbacPolicy.legacyAdmin()")
    @Operation(summary = "Create draft products from the approved rows")
    public ResponseEntity<ApiResponse<ImportJobResponse>> importRows(
            @PathVariable UUID jobId,
            @RequestBody(required = false) ImportSelection selection) {

        List<UUID> rowIds = selection == null ? null : selection.rowIds();
        ProductImportJob job = service.importRows(jobId, rowIds);
        return ApiResponse.success(ImportJobResponse.from(job),
                job.getImportedRows() + " product(s) created as drafts, awaiting photos and a final check.");
    }

    /** Which rows to import. An empty or absent list means every row that extracted cleanly. */
    public record ImportSelection(List<UUID> rowIds) {}
}
