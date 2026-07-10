package com.buyology.ecommerce.quiqup.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.quiqup.config.QuiqupProperties;
import com.buyology.ecommerce.quiqup.domain.QuiqupTestEvent;
import com.buyology.ecommerce.quiqup.dto.QuiqupApiResult;
import com.buyology.ecommerce.quiqup.repository.QuiqupTestEventRepository;
import com.buyology.ecommerce.quiqup.service.QuiqupClient;
import com.buyology.ecommerce.quiqup.service.QuiqupSamples;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin endpoints for the Quiqup <b>staging</b> test module. SUPERADMIN only.
 *
 * <p>Every action that reaches Quiqup is gated on {@code quiqup.enabled} so the module
 * ships inert. Nothing here reads or writes a real order — it is entirely decoupled from
 * the order lifecycle. The dashboard "Quiqup Testing" page drives these endpoints.
 */
@RestController
@RequestMapping("/api/admin/quiqup")
@PreAuthorize("hasRole('SUPERADMIN')")
public class AdminQuiqupController {

    private final QuiqupProperties props;
    private final QuiqupClient client;
    private final QuiqupSamples samples;
    private final QuiqupTestEventRepository eventRepository;
    private final ObjectMapper objectMapper;

    public AdminQuiqupController(QuiqupProperties props, QuiqupClient client, QuiqupSamples samples,
                                 QuiqupTestEventRepository eventRepository, ObjectMapper objectMapper) {
        this.props = props;
        this.client = client;
        this.samples = samples;
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
    }

    public record RawRequest(String method, String path, JsonNode body) {}

    public record QuiqupEventView(String id, String at, String eventType, JsonNode payload,
                                  JsonNode headers, Boolean hmacValid, String sourceIp) {}

    // ── meta (work even when disabled, so the UI can render + prompt to enable) ──

    @GetMapping("/config")
    public ResponseEntity<ApiResponse<Map<String, Object>>> config() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("enabled", props.isEnabled());
        cfg.put("baseUrl", props.getBaseUrl());
        cfg.put("authMode", props.getAuthMode());
        cfg.put("hasApiKey", props.getApiKey() != null && !props.getApiKey().isBlank());
        cfg.put("apiKeyHeader", props.getApiKeyHeader());
        cfg.put("accountId", props.getAccountId());
        cfg.put("webhookSecretConfigured", props.getWebhookSecret() != null && !props.getWebhookSecret().isBlank());
        cfg.put("webhookPath", "/api/quiqup/webhook");
        Map<String, String> paths = new LinkedHashMap<>();
        paths.put("ondemandCreate", props.getPaths().getOndemandCreate());
        paths.put("ondemandGet", props.getPaths().getOndemandGet());
        paths.put("ondemandCancel", props.getPaths().getOndemandCancel());
        paths.put("ondemandQuote", props.getPaths().getOndemandQuote());
        paths.put("ecommerceCreate", props.getPaths().getEcommerceCreate());
        paths.put("ecommerceGet", props.getPaths().getEcommerceGet());
        paths.put("ecommerceCancel", props.getPaths().getEcommerceCancel());
        cfg.put("paths", paths);
        return ApiResponse.success(cfg, "Quiqup config");
    }

    @GetMapping("/samples")
    public ResponseEntity<ApiResponse<Map<String, JsonNode>>> samples() {
        return ApiResponse.success(samples.all(), "Quiqup sample payloads");
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verify() {
        if (disabled()) return disabledMap();
        return ApiResponse.success(client.verify(), "Quiqup auth verified");
    }

    // ── on-demand (point-to-point) ──────────────────────────────────────────────

    @PostMapping("/ondemand/create")
    public ResponseEntity<ApiResponse<QuiqupApiResult>> ondemandCreate(@RequestBody JsonNode body) {
        if (disabled()) return disabledResult();
        return ApiResponse.success(client.request("POST", props.getPaths().getOndemandCreate(), body), "Created");
    }

    @GetMapping("/ondemand/{id}")
    public ResponseEntity<ApiResponse<QuiqupApiResult>> ondemandGet(@PathVariable String id) {
        if (disabled()) return disabledResult();
        return ApiResponse.success(client.request("GET", QuiqupClient.fillPath(props.getPaths().getOndemandGet(), id), null), "Fetched");
    }

    @PostMapping("/ondemand/{id}/cancel")
    public ResponseEntity<ApiResponse<QuiqupApiResult>> ondemandCancel(@PathVariable String id,
                                                                       @RequestBody(required = false) JsonNode body) {
        if (disabled()) return disabledResult();
        return ApiResponse.success(client.request("POST", QuiqupClient.fillPath(props.getPaths().getOndemandCancel(), id), body), "Cancelled");
    }

    @PostMapping("/ondemand/quote")
    public ResponseEntity<ApiResponse<QuiqupApiResult>> ondemandQuote(@RequestBody JsonNode body) {
        if (disabled()) return disabledResult();
        return ApiResponse.success(client.request("POST", props.getPaths().getOndemandQuote(), body), "Quoted");
    }

    // ── ecommerce (scheduled) ───────────────────────────────────────────────────

    @PostMapping("/ecommerce/create")
    public ResponseEntity<ApiResponse<QuiqupApiResult>> ecommerceCreate(@RequestBody JsonNode body) {
        if (disabled()) return disabledResult();
        return ApiResponse.success(client.request("POST", props.getPaths().getEcommerceCreate(), body), "Created");
    }

    @GetMapping("/ecommerce/{id}")
    public ResponseEntity<ApiResponse<QuiqupApiResult>> ecommerceGet(@PathVariable String id) {
        if (disabled()) return disabledResult();
        return ApiResponse.success(client.request("GET", QuiqupClient.fillPath(props.getPaths().getEcommerceGet(), id), null), "Fetched");
    }

    @PostMapping("/ecommerce/{id}/cancel")
    public ResponseEntity<ApiResponse<QuiqupApiResult>> ecommerceCancel(@PathVariable String id,
                                                                        @RequestBody(required = false) JsonNode body) {
        if (disabled()) return disabledResult();
        return ApiResponse.success(client.request("POST", QuiqupClient.fillPath(props.getPaths().getEcommerceCancel(), id), body), "Cancelled");
    }

    // ── raw request tester ──────────────────────────────────────────────────────

    @PostMapping("/raw")
    public ResponseEntity<ApiResponse<QuiqupApiResult>> raw(@RequestBody RawRequest req) {
        if (disabled()) return disabledResult();
        if (req == null || req.path() == null || req.path().isBlank()) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "`path` is required");
        }
        String method = req.method() == null || req.method().isBlank() ? "GET" : req.method();
        return ApiResponse.success(client.request(method, req.path(), req.body()), "Sent");
    }

    // ── received webhooks ───────────────────────────────────────────────────────

    @GetMapping("/events")
    public ResponseEntity<ApiResponse<List<QuiqupEventView>>> events() {
        List<QuiqupEventView> views = eventRepository.findTop100ByOrderByCreatedAtDesc()
                .stream().map(this::toView).toList();
        return ApiResponse.success(views, "Quiqup webhook events");
    }

    @DeleteMapping("/events")
    public ResponseEntity<ApiResponse<Map<String, Object>>> clearEvents() {
        eventRepository.deleteAllInBatch();
        return ApiResponse.success(Map.of("cleared", true), "Cleared");
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private boolean disabled() {
        return !props.isEnabled();
    }

    private ResponseEntity<ApiResponse<QuiqupApiResult>> disabledResult() {
        return ApiResponse.failure(HttpStatus.CONFLICT,
                "Quiqup module is disabled. Set QUIQUP_ENABLED=true (and QUIQUP_API_KEY) to test.");
    }

    private ResponseEntity<ApiResponse<Map<String, Object>>> disabledMap() {
        return ApiResponse.failure(HttpStatus.CONFLICT,
                "Quiqup module is disabled. Set QUIQUP_ENABLED=true (and QUIQUP_API_KEY) to test.");
    }

    private QuiqupEventView toView(QuiqupTestEvent e) {
        return new QuiqupEventView(
                e.getId().toString(),
                e.getCreatedAt() == null ? null : e.getCreatedAt().toString(),
                e.getEventType(),
                readJson(e.getPayload()),
                readJson(e.getHeaders()),
                e.getHmacValid(),
                e.getSourceIp());
    }

    private JsonNode readJson(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return objectMapper.readTree(raw);
        } catch (Exception ex) {
            return objectMapper.valueToTree(raw);
        }
    }
}
