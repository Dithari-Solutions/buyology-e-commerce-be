package com.buyology.ecommerce.quiqup.controller;

import com.buyology.ecommerce.quiqup.config.QuiqupProperties;
import com.buyology.ecommerce.quiqup.domain.QuiqupTestEvent;
import com.buyology.ecommerce.quiqup.repository.QuiqupTestEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Receives Quiqup <b>staging</b> webhook callbacks and logs them for the admin testing page.
 *
 * <p>Security: this endpoint is excluded from JWT auth in SecurityConfig (Quiqup calls it
 * without a token) — it must be added to the permit-all list:
 * <pre>.requestMatchers("/api/quiqup/webhook").permitAll()</pre>
 *
 * <p>It only <b>logs</b> the event; it never mutates a real order. When the module is
 * disabled it is a no-op. Always returns 200 so Quiqup doesn't retry-storm.
 */
@RestController
@RequestMapping("/api/quiqup")
public class QuiqupWebhookController {

    private static final Logger log = LoggerFactory.getLogger(QuiqupWebhookController.class);

    private final QuiqupProperties props;
    private final QuiqupTestEventRepository eventRepository;
    private final ObjectMapper objectMapper;

    public QuiqupWebhookController(QuiqupProperties props, QuiqupTestEventRepository eventRepository,
                                   ObjectMapper objectMapper) {
        this.props = props;
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> receive(HttpServletRequest request) throws IOException {
        // Inert when disabled — accept and drop so Quiqup sees a healthy endpoint.
        if (!props.isEnabled()) {
            return ResponseEntity.ok().build();
        }

        String raw = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        String eventType = null;
        JsonNode root = null;
        if (!raw.isBlank()) {
            try {
                root = objectMapper.readTree(raw);
                for (String key : new String[]{"event", "event_type", "type", "status"}) {
                    if (root.hasNonNull(key)) { eventType = root.get(key).asText(); break; }
                }
            } catch (Exception e) {
                log.warn("[QUIQUP-WEBHOOK] Non-JSON body: {}", e.getMessage());
            }
        }

        Boolean hmacValid = verifyHmac(request, raw);
        Boolean tokenValid = verifySharedToken(request);

        try {
            QuiqupTestEvent event = new QuiqupTestEvent();
            event.setDirection("INBOUND_WEBHOOK");
            event.setEventType(eventType);
            event.setPayload(root != null ? raw : safeJsonWrap(raw));
            event.setHeaders(headersJson(request));
            event.setHmacValid(hmacValid);
            event.setSourceIp(clientIp(request));
            eventRepository.save(event);
            log.info("[QUIQUP-WEBHOOK] stored event={} hmacValid={} tokenValid={}",
                    eventType, hmacValid, tokenValid);
        } catch (Exception e) {
            // Never fail the webhook on a storage error — just log it.
            log.error("[QUIQUP-WEBHOOK] failed to persist event: {}", e.getMessage(), e);
        }

        return ResponseEntity.ok().build();
    }

    /**
     * Check the shared secret we asked Quiqup to send back in a custom header.
     *
     * <p>Deliberately observe-only for now: the result is logged, and a mismatch does NOT reject the
     * delivery. Quiqup's custom-header behaviour is unverified (whether it survives their retries,
     * whether header case is preserved), and silently dropping real delivery events while we find
     * that out would be worse than accepting an unauthenticated one into a log-only table that never
     * touches an order. Turn this into a rejection once deliveries are observed carrying it
     * reliably.
     *
     * @return {@code null} when no token is configured, otherwise whether the header matched.
     */
    private Boolean verifySharedToken(HttpServletRequest request) {
        String expected = props.getWebhookToken();
        if (expected == null || expected.isBlank()) return null;

        String headerName = props.getWebhookTokenHeader();
        String provided = headerName == null || headerName.isBlank()
                ? null
                : request.getHeader(headerName); // getHeader is case-insensitive per the servlet spec

        if (provided == null || provided.isBlank()) {
            log.warn("[QUIQUP-WEBHOOK] shared token expected in '{}' but the header was absent", headerName);
            return false;
        }
        // Constant-time compare so a mismatch can't be narrowed down by timing.
        boolean ok = java.security.MessageDigest.isEqual(
                provided.trim().getBytes(StandardCharsets.UTF_8),
                expected.trim().getBytes(StandardCharsets.UTF_8));
        if (!ok) log.warn("[QUIQUP-WEBHOOK] shared token in '{}' did not match", headerName);
        return ok;
    }

    /**
     * Verify the webhook signature when a secret is configured. Returns null when no secret
     * is set (nothing to verify). Quiqup's exact signing scheme is confirmed during testing;
     * this checks common HMAC-SHA256 hex/base64 over the raw body against typical headers.
     */
    private Boolean verifyHmac(HttpServletRequest request, String raw) {
        String secret = props.getWebhookSecret();
        if (secret == null || secret.isBlank()) return null;
        String provided = firstNonBlank(
                request.getHeader("X-Quiqup-Signature"),
                request.getHeader("X-Signature"),
                request.getHeader("X-Webhook-Signature"));
        if (provided == null) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(raw.getBytes(StandardCharsets.UTF_8));
            String hex = toHex(digest);
            String b64 = java.util.Base64.getEncoder().encodeToString(digest);
            String cleaned = provided.replaceFirst("^sha256=", "").trim();
            return cleaned.equalsIgnoreCase(hex) || cleaned.equals(b64);
        } catch (Exception e) {
            log.warn("[QUIQUP-WEBHOOK] HMAC check failed: {}", e.getMessage());
            return false;
        }
    }

    private String headersJson(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        var names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String n = names.nextElement();
            // Don't persist opaque cookies/auth in the test log
            if (n.equalsIgnoreCase("cookie") || n.equalsIgnoreCase("authorization")) continue;
            headers.put(n, request.getHeader(n));
        }
        try {
            return objectMapper.writeValueAsString(headers);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String safeJsonWrap(String raw) {
        try {
            return objectMapper.writeValueAsString(Map.of("raw", raw));
        } catch (Exception e) {
            return "{}";
        }
    }

    private static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return request.getRemoteAddr();
    }

    private static String firstNonBlank(String... vals) {
        for (String v : vals) if (v != null && !v.isBlank()) return v;
        return null;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
