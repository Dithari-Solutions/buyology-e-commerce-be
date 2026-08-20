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

        // The signature covers the body's exact bytes, so those are what gets kept and signed. The
        // String below is only for parsing and for the admin page: decoding to UTF-16 and re-encoding
        // is lossless for well-formed UTF-8 but replaces any malformed byte with U+FFFD, which would
        // silently change the digest. Quiqup call re-serialisation the most common cause of a
        // signature mismatch, and this is the version of that mistake that is easiest to miss.
        byte[] bodyBytes = request.getInputStream().readAllBytes();
        String raw = new String(bodyBytes, StandardCharsets.UTF_8);

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

        // null = no secret configured, true = signature verified, false = it did not.
        Boolean hmacValid = verifySignature(request, bodyBytes, raw);
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

        // Rejection is decided after storing, never instead of it: a delivery turned away is exactly
        // the one worth inspecting, and the admin Webhooks tab is where it is visible regardless of
        // which instance behind the load balancer received it. 401 rather than 200 so the rejection
        // also shows as a failure in Quiqup's delivery-logs and can be replayed from there.
        // Enforcement means "accept only what verified", not "reject only what failed". hmacValid is
        // null whenever verification could not run at all — no secret configured, or no signature
        // header on the delivery — and the previous form tested Boolean.FALSE.equals(hmacValid),
        // which is false for null. So switching enforcement on while the secret was missing accepted
        // every forged event with a 200 while the config page reported enforcement as on: the one
        // configuration where an operator is most certain they are protected.
        if (props.isWebhookRequireSignature() && !Boolean.TRUE.equals(hmacValid)) {
            log.warn("[QUIQUP-WEBHOOK] rejected event={} — signature {} and "
                    + "quiqup.webhook-require-signature is on", eventType,
                    hmacValid == null ? "could not be verified" : "did not verify");
            return ResponseEntity.status(401).build();
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

    /** Header carrying the hex HMAC. Confirmed by Quiqup API support, 2026-08-12. */
    private static final String SIGNATURE_HEADER = "X-Quiqup-Signature";

    /** Header carrying the timestamp that prefixes the signed string. */
    private static final String TIMESTAMP_HEADER = "X-Quiqup-Timestamp";

    /**
     * Verify the delivery signature against the scheme Quiqup confirmed in writing:
     *
     * <pre>
     *   X-Quiqup-Signature = hex( HMAC-SHA256( subscription secret,
     *                                          X-Quiqup-Timestamp + "." + raw request body ) )
     * </pre>
     *
     * <p>Hex, lower case, no {@code sha256=} prefix, and the body must be the bytes exactly as
     * received — which is why {@code body} is a {@code byte[]} here and never a re-encoded String.
     *
     * <p><b>Do not "correct" this against Quiqup's public documentation.</b> Their published docs
     * still describe an older SHA-1 scheme; support confirmed the docs are wrong and are being
     * updated, and that the above is what their dispatcher actually sends.
     *
     * @return null when no secret is configured, otherwise whether the signature verified
     */
    // Package-private so QuiqupWebhookSignatureTest can pin it against Quiqup's own reference vector.
    Boolean verifySignature(HttpServletRequest request, byte[] body, String raw) {
        String secret = props.getWebhookSecret();
        if (secret == null || secret.isBlank()) return null;

        String provided = request.getHeader(SIGNATURE_HEADER);
        String timestamp = request.getHeader(TIMESTAMP_HEADER);
        if (provided == null || provided.isBlank()) {
            log.warn("[QUIQUP-WEBHOOK] no {} header on the delivery. headers seen={}",
                    SIGNATURE_HEADER, signatureLookingHeaders(request));
            return false;
        }
        if (timestamp == null || timestamp.isBlank()) {
            log.warn("[QUIQUP-WEBHOOK] {} present but {} missing — the signed string cannot be rebuilt",
                    SIGNATURE_HEADER, TIMESTAMP_HEADER);
            return false;
        }

        byte[] signed = concat((timestamp.trim() + ".").getBytes(StandardCharsets.UTF_8), body);
        byte[] mac = hmacSha256(secret.getBytes(StandardCharsets.UTF_8), signed);
        if (mac == null) return false;

        String expected = toHex(mac);
        String actual = provided.trim().toLowerCase(java.util.Locale.ROOT);
        boolean ok = java.security.MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII));

        if (ok) {
            log.info("[QUIQUP-WEBHOOK] signature verified (skew={})", describeSkew(timestamp));
            return true;
        }

        // Say *why* it failed. A mismatch is one of: wrong secret, wrong construction, or a body that
        // is not byte-identical to what was signed — and those need different fixes. If one of the
        // other constructions matches, the secret is right and the spec has moved.
        SignatureMatch alternative = identifySignature(request, raw);
        if (alternative != null) {
            log.warn("[QUIQUP-WEBHOOK] signature mismatch, but '{}' in header '{}' DID match — "
                            + "the documented scheme appears to have changed",
                    alternative.scheme(), alternative.headerName());
        } else {
            log.warn("[QUIQUP-WEBHOOK] signature mismatch and no known construction matched. "
                            + "Most likely the wrong subscription secret (they are per-environment: "
                            + "staging and production subscriptions have different secrets). "
                            + "timestamp={} skew={} candidates(first 16 chars)={}",
                    timestamp, describeSkew(timestamp), truncatedCandidates(secret, raw, request));
        }
        return false;
    }

    /**
     * How far the delivery's timestamp is from now, for the log only.
     *
     * <p>Recorded rather than enforced: a replay window is the right control once these events
     * actually drive an order, but Quiqup retry delayed deliveries and no tolerance has been observed
     * yet, so rejecting on age today would drop legitimate retries. The numbers here are what a
     * sensible window gets chosen from.
     */
    private String describeSkew(String timestamp) {
        try {
            long value = Long.parseLong(timestamp.trim());
            // Accept seconds or milliseconds — the format is not documented.
            long epochSeconds = value > 100_000_000_000L ? value / 1000 : value;
            return (java.time.Instant.now().getEpochSecond() - epochSeconds) + "s";
        } catch (Exception e) {
            return "unparseable(" + timestamp + ")";
        }
    }

    /** Which header carried the signature, and how it was constructed. */
    record SignatureMatch(String headerName, String scheme) {}

    /**
     * Work out how Quiqup signed this delivery, by computing every plausible signature and looking
     * for one of them in <em>any</em> incoming header.
     *
     * <p>Quiqup support have confirmed only the hash family: "we use SHA256". That leaves three
     * things undocumented, and a signature check needs all three to be right:
     * <ul>
     *   <li><b>which header</b> carries it — guessing a fixed list means a correct secret still
     *       reads as "invalid" if they use a name we didn't think of,</li>
     *   <li><b>the encoding</b> — lowercase hex or base64,</li>
     *   <li><b>what is actually signed</b> — "SHA256" names a hash, not a construction, so it may be
     *       HMAC-SHA256 keyed with the shared secret, or a plain digest of the secret concatenated
     *       with the body, and possibly with a timestamp mixed in.</li>
     * </ul>
     *
     * <p>So rather than assert one guess, this tries them all and reports which combination matched.
     * Quiqup are about to drive a test order through its states, which is the one window where real
     * signed deliveries arrive — that round should end with the scheme known rather than with another
     * email. Once it is confirmed, collapse this back to the single construction and reject
     * mismatches.
     *
     * @return the winning header + scheme, or null if nothing matched (or no secret is configured)
     */
    private SignatureMatch identifySignature(HttpServletRequest request, String raw) {
        String secret = props.getWebhookSecret();
        if (secret == null || secret.isBlank()) return null;

        Map<String, String> candidates = signatureCandidates(secret, raw, request);

        var names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            String value = request.getHeader(name);
            if (value == null || value.isBlank()) continue;
            // Providers commonly prefix the algorithm ("sha256=abc…"); compare the digest itself.
            String cleaned = value.trim()
                    .replaceFirst("(?i)^(sha256|hmac-sha256|hmacsha256)\\s*=\\s*", "")
                    .trim();
            for (var candidate : candidates.entrySet()) {
                if (cleaned.equalsIgnoreCase(candidate.getValue())) {
                    return new SignatureMatch(name, candidate.getKey());
                }
            }
        }
        return null;
    }

    /**
     * Every signature we know how to compute for this body, keyed by a human-readable scheme name.
     *
     * <p>Base64 is compared case-sensitively in practice but stored here as-is; the caller's
     * {@code equalsIgnoreCase} is harmless for base64 because a case-flipped base64 string is
     * astronomically unlikely to collide with the correct one.
     */
    private Map<String, String> signatureCandidates(String secret, String raw, HttpServletRequest request) {
        Map<String, String> candidates = new LinkedHashMap<>();
        byte[] body = raw.getBytes(StandardCharsets.UTF_8);
        byte[] key = secret.getBytes(StandardCharsets.UTF_8);

        byte[] hmac = hmacSha256(key, body);
        if (hmac != null) {
            candidates.put("HMAC_SHA256(body) hex", toHex(hmac));
            candidates.put("HMAC_SHA256(body) base64", java.util.Base64.getEncoder().encodeToString(hmac));
        }

        // The literal reading of "we use SHA256": a plain digest, not a keyed MAC.
        byte[] secretThenBody = sha256(concat(key, body));
        if (secretThenBody != null) {
            candidates.put("SHA256(secret+body) hex", toHex(secretThenBody));
            candidates.put("SHA256(secret+body) base64", java.util.Base64.getEncoder().encodeToString(secretThenBody));
        }
        byte[] bodyThenSecret = sha256(concat(body, key));
        if (bodyThenSecret != null) {
            candidates.put("SHA256(body+secret) hex", toHex(bodyThenSecret));
            candidates.put("SHA256(body+secret) base64", java.util.Base64.getEncoder().encodeToString(bodyThenSecret));
        }

        // Stripe-style "<timestamp>.<body>" signing, for any timestamp-looking header they send.
        var names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            String lower = name.toLowerCase();
            if (!lower.contains("timestamp") && !(lower.contains("time") && !lower.equals("date"))) continue;
            String ts = request.getHeader(name);
            if (ts == null || ts.isBlank()) continue;
            byte[] signed = (ts.trim() + "." + raw).getBytes(StandardCharsets.UTF_8);
            byte[] tsHmac = hmacSha256(key, signed);
            if (tsHmac != null) {
                candidates.put("HMAC_SHA256(" + name + " + '.' + body) hex", toHex(tsHmac));
                candidates.put("HMAC_SHA256(" + name + " + '.' + body) base64",
                        java.util.Base64.getEncoder().encodeToString(tsHmac));
            }
        }
        return candidates;
    }

    private byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            log.warn("[QUIQUP-WEBHOOK] HMAC-SHA256 unavailable: {}", e.getMessage());
            return null;
        }
    }

    private byte[] sha256(byte[] data) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256").digest(data);
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    /**
     * The candidate signatures, truncated to their first 16 characters.
     *
     * <p>Truncated on purpose: enough to match by eye against the header value shown on the admin
     * Webhooks tab, not enough to be a usable signature if the log is ever shared. The secret itself
     * is never logged, and a digest cannot be reversed into it.
     */
    private String truncatedCandidates(String secret, String raw, HttpServletRequest request) {
        Map<String, String> shortened = new LinkedHashMap<>();
        signatureCandidates(secret, raw, request).forEach((scheme, value) ->
                shortened.put(scheme, value.length() <= 16 ? value : value.substring(0, 16) + "…"));
        return shortened.toString();
    }

    /**
     * Names of headers that look like they carry a signature, so a failed match can be diagnosed
     * from what actually arrived. Mirrors the highlighting on the admin Webhooks tab.
     */
    private String signatureLookingHeaders(HttpServletRequest request) {
        Map<String, String> found = new LinkedHashMap<>();
        var names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            String lower = name.toLowerCase();
            if (lower.contains("sign") || lower.contains("hmac") || lower.contains("hash")
                    || lower.contains("digest") || lower.contains("timestamp")) {
                found.put(name, request.getHeader(name));
            }
        }
        return found.isEmpty() ? "(none)" : found.toString();
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

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
