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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
 * <p>Wraps Quiqup's unified {@code /orders} API. Every action that reaches Quiqup is gated
 * on {@code quiqup.enabled} so the module ships inert. Nothing here reads or writes a real
 * order — it is entirely decoupled from the order lifecycle.
 */
@RestController
@RequestMapping("/api/admin/quiqup")
public class AdminQuiqupController {

    private final QuiqupProperties props;
    private final QuiqupClient client;
    private final QuiqupSamples samples;
    private final QuiqupTestEventRepository eventRepository;
    private final ObjectMapper objectMapper;
    private final com.buyology.ecommerce.quiqup.service.QuiqupDispatchService dispatchService;
    private final com.buyology.ecommerce.quiqup.service.QuiqupCancelService cancelService;

    public AdminQuiqupController(QuiqupProperties props, QuiqupClient client, QuiqupSamples samples,
                                 QuiqupTestEventRepository eventRepository, ObjectMapper objectMapper,
                                 com.buyology.ecommerce.quiqup.service.QuiqupDispatchService dispatchService,
                                 com.buyology.ecommerce.quiqup.service.QuiqupCancelService cancelService) {
        this.props = props;
        this.client = client;
        this.samples = samples;
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
        this.dispatchService = dispatchService;
        this.cancelService = cancelService;
    }

    public record RawRequest(String method, String path, JsonNode body) {}

    public record CancelRequest(String id) {}

    public record QuiqupEventView(String id, String at, String eventType, JsonNode payload,
                                  JsonNode headers, Boolean hmacValid, String sourceIp) {}

    // ── meta (work even when disabled, so the UI can render + prompt to enable) ──

    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('quiqup:read') or @rbacPolicy.legacyAdmin()")
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
        // Surfaced so the testing page can say plainly which estate it is pointed at, and whether a
        // write would be refused, before anyone clicks Create Order.
        boolean staging = props.getBaseUrl() != null && props.getBaseUrl().toLowerCase().contains("staging");
        cfg.put("staging", staging);
        cfg.put("allowProductionWrites", props.isAllowProductionWrites());
        cfg.put("writesBlocked", !staging && !props.isAllowProductionWrites());
        Map<String, String> paths = new LinkedHashMap<>();
        paths.put("create", props.getPaths().getCreate());
        paths.put("get", props.getPaths().getGet());
        paths.put("readyForCollection", props.getPaths().getReadyForCollection());
        paths.put("cancel", props.getPaths().getCancel());
        paths.put("label", props.getPaths().getLabel());
        cfg.put("paths", paths);
        return ApiResponse.success(cfg, "Quiqup config");
    }

    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('quiqup:read') or @rbacPolicy.legacyAdmin()")
    @GetMapping("/samples")
    public ResponseEntity<ApiResponse<Map<String, JsonNode>>> samples() {
        return ApiResponse.success(samples.all(), "Quiqup sample payloads");
    }

    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('quiqup:read') or @rbacPolicy.legacyAdmin()")
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<Map<String, Object>>> verify() {
        if (disabled()) return disabledMap();
        return ApiResponse.success(client.verify(), "Quiqup auth verified");
    }

    // ── orders (unified /orders API) ─────────────────────────────────────────────

    /** Create an order. POST /orders */
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('quiqup:order:create') or @rbacPolicy.legacyAdmin()")
    @PostMapping("/orders")
    public ResponseEntity<ApiResponse<QuiqupApiResult>> create(@RequestBody JsonNode body) {
        if (disabled()) return disabledResult();
        return ApiResponse.success(client.request("POST", props.getPaths().getCreate(), body), "Created");
    }

    /** Retrieve an order. GET /orders/{id} */
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('quiqup:read') or @rbacPolicy.legacyAdmin()")
    @GetMapping("/orders/{id}")
    public ResponseEntity<ApiResponse<QuiqupApiResult>> get(@PathVariable String id) {
        if (disabled()) return disabledResult();
        return ApiResponse.success(client.request("GET", QuiqupClient.fillPath(props.getPaths().getGet(), id), null), "Fetched");
    }

    /** Mark an order ready for collection (triggers pickup). PUT /orders/{id}/ready_for_collection */
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('quiqup:order:update') or @rbacPolicy.legacyAdmin()")
    @PutMapping("/orders/{id}/ready")
    public ResponseEntity<ApiResponse<QuiqupApiResult>> ready(@PathVariable String id) {
        if (disabled()) return disabledResult();
        return ApiResponse.success(
                client.request("PUT", QuiqupClient.fillPath(props.getPaths().getReadyForCollection(), id), null),
                "Marked ready for collection");
    }

    /** Download the AWB document metadata. GET /order_label/{id} */
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('quiqup:read') or @rbacPolicy.legacyAdmin()")
    @GetMapping("/orders/{id}/label")
    public ResponseEntity<ApiResponse<QuiqupApiResult>> label(@PathVariable String id) {
        if (disabled()) return disabledResult();
        return ApiResponse.success(client.request("GET", QuiqupClient.fillPath(props.getPaths().getLabel(), id), null), "Label");
    }

    /** Cancel an order via the batch endpoint. PUT /orders/batch/set_cancelled  (id in body). */
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('quiqup:order:cancel') or @rbacPolicy.legacyAdmin()")
    @PostMapping("/orders/cancel")
    public ResponseEntity<ApiResponse<QuiqupApiResult>> cancel(@RequestBody CancelRequest req) {
        if (disabled()) return disabledResult();
        if (req == null || req.id() == null || req.id().isBlank()) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "`id` is required");
        }
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode ids = body.putArray("order_ids");
        ids.add(req.id());
        return ApiResponse.success(client.request("PUT", props.getPaths().getCancel(), body), "Cancelled");
    }

    // ── raw request tester ──────────────────────────────────────────────────────

    @PreAuthorize("hasRole('SUPERADMIN')")
    @PostMapping("/raw")
    public ResponseEntity<ApiResponse<QuiqupApiResult>> raw(@RequestBody RawRequest req) {
        if (disabled()) return disabledResult();
        if (req == null || req.path() == null || req.path().isBlank()) {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "`path` is required");
        }
        String method = req.method() == null || req.method().isBlank() ? "GET" : req.method();
        return ApiResponse.success(client.request(method, req.path(), req.body()), "Sent");
    }

    // ── Dispatching one of OUR orders ────────────────────────────────────────
    // Separate from the /orders endpoints above, which take a hand-written body and know nothing
    // about our orders. These two take an order id and build the payload from it, which is the
    // path a paid order actually travels.

    /**
     * Shows exactly what would be sent for one of our orders, and sends nothing.
     *
     * <p>This exists because the costly errors in a delivery payload do not announce themselves. A
     * transposed coordinate is still a valid coordinate; an origin and destination the wrong way
     * round is still a well-formed job. Nothing rejects either, and the first report is a courier at
     * the wrong address. Reading the real payload for a real order costs nothing and catches both.
     */
    @GetMapping("/dispatch/{orderId}/preview")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('quiqup:read')")
    public ResponseEntity<ApiResponse<com.buyology.ecommerce.quiqup.service.QuiqupDispatchService.DispatchPreview>>
            previewDispatch(@PathVariable java.util.UUID orderId) {
        return ApiResponse.success(dispatchService.preview(orderId), "Dispatch preview");
    }

    /**
     * Dispatches exactly one of our orders, on demand.
     *
     * <p>Deliberately independent of {@code quiqup.dispatch.enabled}. That flag is all-or-nothing:
     * switching it on starts dispatching every qualifying paid order, which is not a thing anyone
     * should have to do to test one. This dispatches the order named in the URL and nothing else,
     * so a first live delivery can be a chosen order rather than whichever customer happens to
     * check out next.
     *
     * <p>Idempotent for the same reason the automatic path is: an order that already carries a
     * Quiqup id is left alone, and the claim means two admins pressing this at once produce one job.
     */
    @PostMapping("/dispatch/{orderId}")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('quiqup:order:create')")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> dispatchOrder(
            @PathVariable java.util.UUID orderId) {
        String outcome = dispatchService.dispatch(orderId);
        return ApiResponse.success(java.util.Map.of("orderId", orderId.toString(), "outcome", outcome),
                "Dispatch attempted");
    }

    /**
     * Retries stopping the Quiqup job for an ORDER id — the manual-recovery half of the cancel leg.
     *
     * <p>Takes our own order id rather than a hand-typed Quiqup job id, which is what made the old
     * cancel control unusable: the dashboard never displayed the job id it demanded. An admin
     * looking at a cancelled order whose refund is held (quiqupCancelStatus REFUSED_TOO_LATE or
     * NEEDS_HUMAN, surfaced on the admin order view) presses this; the outcome and its consequences
     * are decided by exactly the same service and gates as the automatic path.
     */
    @io.swagger.v3.oas.annotations.Operation(summary = "Retry stopping the Quiqup job for a cancelled order")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('quiqup:order:cancel') or @rbacPolicy.legacyAdmin()")
    @PostMapping("/cancel/{orderId}")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> cancelForOrder(
            @PathVariable java.util.UUID orderId) {
        var result = cancelService.cancelForOrder(orderId, "Manual retry from the admin dashboard");
        return ApiResponse.success(java.util.Map.of(
                        "orderId", orderId.toString(),
                        "outcome", result.outcome().name(),
                        "refundAllowed", result.refundAllowed(),
                        "detail", result.detail() == null ? "" : result.detail()),
                "Cancel attempted");
    }

    // ── received webhooks ───────────────────────────────────────────────────────

    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('quiqup:read') or @rbacPolicy.legacyAdmin()")
    @GetMapping("/events")
    public ResponseEntity<ApiResponse<List<QuiqupEventView>>> events() {
        List<QuiqupEventView> views = eventRepository.findTop100ByOrderByCreatedAtDesc()
                .stream().map(this::toView).toList();
        return ApiResponse.success(views, "Quiqup webhook events");
    }

    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('quiqup:event:delete') or @rbacPolicy.legacyAdmin()")
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
