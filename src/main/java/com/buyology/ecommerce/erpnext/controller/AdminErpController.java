package com.buyology.ecommerce.erpnext.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.erpnext.config.ErpNextProperties;
import com.buyology.ecommerce.erpnext.dto.ErpImportPreviewRow;
import com.buyology.ecommerce.erpnext.dto.ErpImportResult;
import com.buyology.ecommerce.erpnext.dto.ErpOrderSyncView;
import com.buyology.ecommerce.erpnext.dto.ErpProduct;
import com.buyology.ecommerce.erpnext.service.ErpNextClient;
import com.buyology.ecommerce.erpnext.service.ErpNextClient.ErpNextException;
import com.buyology.ecommerce.erpnext.service.ErpOrderSyncService;
import com.buyology.ecommerce.erpnext.service.ErpProductImportService;
import com.buyology.ecommerce.order.domain.Order;
import com.buyology.ecommerce.order.repository.OrderRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin endpoints for the ERPNext integration (testing stage). SUPERADMIN only.
 *
 * <p>Reads the ERPNext {@code Item} list and relays it to the dashboard. Nothing is written to
 * our database — this exists to validate connectivity before a real product sync is built.
 * Gated on {@code erpnext.enabled} so the module ships inert.
 */
@RestController
@RequestMapping("/api/admin/erp")
public class AdminErpController {

    /** Default number of products to pull for the testing page. */
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 100;

    private final ErpNextProperties props;
    private final ErpNextClient client;
    private final ErpOrderSyncService orderSyncService;
    private final ErpProductImportService productImportService;
    private final OrderRepository orderRepo;

    public AdminErpController(ErpNextProperties props, ErpNextClient client,
                              ErpOrderSyncService orderSyncService,
                              ErpProductImportService productImportService,
                              OrderRepository orderRepo) {
        this.props = props;
        this.client = client;
        this.orderSyncService = orderSyncService;
        this.productImportService = productImportService;
        this.orderRepo = orderRepo;
    }

    public record ImportRequest(List<String> itemCodes) {}

    public record MockOrderRequest(List<String> itemCodes, String currency) {}

    /** Meta — works even when disabled so the UI can render and prompt to enable. */
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('erp:read') or @rbacPolicy.legacyAdmin()")
    @GetMapping("/config")
    public ResponseEntity<ApiResponse<Map<String, Object>>> config() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("enabled", props.isEnabled());
        cfg.put("baseUrl", props.getBaseUrl());
        cfg.put("hasApiKey", props.getApiKey() != null && !props.getApiKey().isBlank());
        cfg.put("hasApiSecret", props.getApiSecret() != null && !props.getApiSecret().isBlank());
        cfg.put("syncOrders", props.isSyncOrders());
        cfg.put("submitDocuments", props.isSubmitDocuments());
        cfg.put("company", props.getCompany());
        cfg.put("autoCreateCustomer", props.isAutoCreateCustomer());
        cfg.put("autoCreateItems", props.isAutoCreateItems());
        cfg.put("shippingAccountHead", props.getShippingAccountHead());
        return ApiResponse.success(cfg, "ERPNext config");
    }

    /**
     * Fetch the first {@code limit} products (default 10) live from ERPNext. No DB persistence.
     */
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('erp:read') or @rbacPolicy.legacyAdmin()")
    @GetMapping("/products")
    public ResponseEntity<ApiResponse<List<ErpProduct>>> products(
            @RequestParam(name = "limit", defaultValue = "" + DEFAULT_LIMIT) int limit) {
        if (!props.isEnabled()) {
            return ApiResponse.failure(HttpStatus.CONFLICT,
                    "ERPNext module is disabled. Set ERPNEXT_ENABLED=true (and base URL, key, secret) to test.");
        }
        int clamped = Math.max(1, Math.min(limit, MAX_LIMIT));
        try {
            return ApiResponse.success(client.listProducts(clamped), "Fetched " + clamped + " products from ERPNext");
        } catch (ErpNextException e) {
            return ApiResponse.failure(HttpStatus.BAD_GATEWAY, e.getMessage());
        }
    }

    // ── ERPNext → catalog import ─────────────────────────────────────────────

    /**
     * Preview a page of ERP items with warehouse stock and whether each is already imported.
     * Read-only — nothing is written to the catalog.
     */
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('erp:product:import') or @rbacPolicy.legacyAdmin()")
    @GetMapping("/import/preview")
    public ResponseEntity<ApiResponse<List<ErpImportPreviewRow>>> importPreview(
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @RequestParam(name = "offset", defaultValue = "0") int offset) {
        if (!props.isEnabled()) {
            return ApiResponse.failure(HttpStatus.CONFLICT,
                    "ERPNext module is disabled. Set ERPNEXT_ENABLED=true (and base URL, key, secret) to test.");
        }
        int clamped = Math.max(1, Math.min(limit, MAX_LIMIT));
        try {
            return ApiResponse.success(productImportService.preview(clamped, Math.max(0, offset)),
                    "ERP items to import");
        } catch (ErpNextException e) {
            return ApiResponse.failure(HttpStatus.BAD_GATEWAY, e.getMessage());
        }
    }

    /**
     * Import (create-or-update) the selected ERP items into the general products list.
     * Idempotent by SKU; does not assign products to any store.
     */
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('erp:product:import') or @rbacPolicy.legacyAdmin()")
    @PostMapping("/import")
    public ResponseEntity<ApiResponse<List<ErpImportResult>>> importProducts(@RequestBody ImportRequest req) {
        if (!props.isEnabled()) {
            return ApiResponse.failure(HttpStatus.CONFLICT,
                    "ERPNext module is disabled. Set ERPNEXT_ENABLED=true (and base URL, key, secret) to test.");
        }
        if (req == null || req.itemCodes() == null || req.itemCodes().isEmpty()) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "`itemCodes` is required");
        }
        try {
            return ApiResponse.success(productImportService.importByCodes(req.itemCodes()),
                    "Imported " + req.itemCodes().size() + " item(s) from ERPNext");
        } catch (ErpNextException e) {
            return ApiResponse.failure(HttpStatus.BAD_GATEWAY, e.getMessage());
        }
    }

    // ── order → ERPNext sync ─────────────────────────────────────────────────

    /**
     * Push a synthetic mock order to ERPNext through the same code path a real PAID order uses
     * (Customer → Sales Order → Sales Invoice). Nothing is written to the orders table — this is
     * purely a connectivity/field-mapping test of the live ERPNext write path.
     */
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('erp:order:mock') or @rbacPolicy.legacyAdmin()")
    @PostMapping("/orders/mock")
    public ResponseEntity<ApiResponse<ErpOrderSyncService.MockResult>> mockOrder(
            @RequestBody(required = false) MockOrderRequest req) {
        if (!props.isEnabled()) {
            return ApiResponse.failure(HttpStatus.CONFLICT,
                    "ERPNext module is disabled. Set ERPNEXT_ENABLED=true (and base URL, key, secret) to test.");
        }
        List<String> codes = req == null ? null : req.itemCodes();
        String currency = req == null ? null : req.currency();
        ErpOrderSyncService.MockResult result = orderSyncService.createMockOrder(codes, currency);
        if (!result.ok()) {
            return ApiResponse.failure(HttpStatus.BAD_GATEWAY, result.message());
        }
        return ApiResponse.success(result, "Mock order pushed to ERPNext");
    }

    /**
     * Recent orders with their ERPNext sync state, so an admin can confirm the Sales Order
     * and Sales Invoice were created (and see why not, when a push failed).
     */
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('erp:order:sync') or @rbacPolicy.legacyAdmin()")
    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<List<ErpOrderSyncView>>> orders(
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        int clamped = Math.max(1, Math.min(limit, MAX_LIMIT));
        List<ErpOrderSyncView> views = orderRepo
                .findAll(PageRequest.of(0, clamped, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(this::toView)
                .getContent();
        return ApiResponse.success(views, "Recent orders with ERPNext sync state");
    }

    /**
     * Push one order to ERPNext now. Idempotent — an order that already has a Sales Invoice
     * is reported as already synced rather than duplicated.
     */
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('erp:order:sync') or @rbacPolicy.legacyAdmin()")
    @PostMapping("/orders/{id}/sync")
    public ResponseEntity<ApiResponse<Map<String, Object>>> syncOrder(@PathVariable("id") UUID id) {
        if (!props.isEnabled()) {
            return ApiResponse.failure(HttpStatus.CONFLICT,
                    "ERPNext module is disabled. Set ERPNEXT_ENABLED=true (and base URL, key, secret) to test.");
        }
        if (!props.isSyncOrders()) {
            return ApiResponse.failure(HttpStatus.CONFLICT,
                    "Order sync is disabled. Set ERPNEXT_SYNC_ORDERS=true to push orders to ERPNext.");
        }
        String outcome = orderSyncService.syncOrder(id);
        Order order = orderRepo.findById(id).orElse(null);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("outcome", outcome);
        result.put("order", order == null ? null : toView(order));
        return ApiResponse.success(result, outcome);
    }

    private ErpOrderSyncView toView(Order o) {
        return new ErpOrderSyncView(
                o.getId().toString(),
                o.getStatus() == null ? null : o.getStatus().name(),
                o.getTotalAmount(),
                o.getCurrency(),
                o.getPaidAt() == null ? null : o.getPaidAt().toString(),
                o.getErpSalesOrder(),
                o.getErpSalesInvoice(),
                o.getErpSyncedAt() == null ? null : o.getErpSyncedAt().toString(),
                o.getErpSyncError(),
                client.deskUrl("Sales Order", o.getErpSalesOrder()),
                client.deskUrl("Sales Invoice", o.getErpSalesInvoice()));
    }
}
