package com.buyology.ecommerce.erpnext.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.erpnext.config.ErpNextProperties;
import com.buyology.ecommerce.erpnext.dto.ErpProduct;
import com.buyology.ecommerce.erpnext.service.ErpNextClient;
import com.buyology.ecommerce.erpnext.service.ErpNextClient.ErpNextException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin endpoints for the ERPNext integration (testing stage). SUPERADMIN only.
 *
 * <p>Reads the ERPNext {@code Item} list and relays it to the dashboard. Nothing is written to
 * our database — this exists to validate connectivity before a real product sync is built.
 * Gated on {@code erpnext.enabled} so the module ships inert.
 */
@RestController
@RequestMapping("/api/admin/erp")
@PreAuthorize("hasRole('SUPERADMIN')")
public class AdminErpController {

    /** Default number of products to pull for the testing page. */
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 100;

    private final ErpNextProperties props;
    private final ErpNextClient client;

    public AdminErpController(ErpNextProperties props, ErpNextClient client) {
        this.props = props;
        this.client = client;
    }

    /** Meta — works even when disabled so the UI can render and prompt to enable. */
    @GetMapping("/config")
    public ResponseEntity<ApiResponse<Map<String, Object>>> config() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("enabled", props.isEnabled());
        cfg.put("baseUrl", props.getBaseUrl());
        cfg.put("hasApiKey", props.getApiKey() != null && !props.getApiKey().isBlank());
        cfg.put("hasApiSecret", props.getApiSecret() != null && !props.getApiSecret().isBlank());
        return ApiResponse.success(cfg, "ERPNext config");
    }

    /**
     * Fetch the first {@code limit} products (default 10) live from ERPNext. No DB persistence.
     */
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
}
