package com.buyology.ecommerce.quiqup.service;

import com.buyology.ecommerce.quiqup.config.QuiqupProperties;
import com.buyology.ecommerce.quiqup.dto.QuiqupApiResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thin authenticated HTTP client for the Quiqup <b>staging</b> API.
 *
 * <p>Self-contained (builds its own {@link WebClient} from {@link QuiqupProperties#getBaseUrl()},
 * like the courier client) so it depends on no shared bean. Captures the status + body of any
 * response — 2xx or not — so the admin UI can see exactly what Quiqup returned. Never touches
 * the order domain.
 */
@Component
public class QuiqupClient {

    private static final Logger log = LoggerFactory.getLogger(QuiqupClient.class);

    private final QuiqupProperties props;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    // Cached OAuth token (oauth mode only)
    private volatile String cachedToken;
    private volatile Instant tokenExpiry = Instant.EPOCH;

    public QuiqupClient(QuiqupProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        // A short connect timeout separates "cannot reach the host at all" (blocked egress, DNS)
        // from "connected but Quiqup is slow to answer". Without it both surface as the same
        // opaque TimeoutException and there is no way to tell which from the admin page.
        reactor.netty.http.client.HttpClient httpClient = reactor.netty.http.client.HttpClient.create()
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        (int) Math.min(props.getConnectTimeoutMs(), Integer.MAX_VALUE))
                .responseTimeout(Duration.ofMillis(props.getTimeoutMs()));

        this.webClient = WebClient.builder()
                .baseUrl(props.getBaseUrl())
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
                .build();

        if (props.isEnabled()) {
            log.info("[QUIQUP] enabled — baseUrl={} authMode={} apiKey={} staging={} productionWrites={}",
                    props.getBaseUrl(), props.getAuthMode(),
                    props.getApiKey() == null || props.getApiKey().isBlank() ? "MISSING" : "present",
                    isStagingBaseUrl(), props.isAllowProductionWrites());
        }
    }

    /** Fill {@code {id}} placeholders in a path template. */
    public static String fillPath(String template, String id) {
        return template.replace("{id}", id == null ? "" : id);
    }

    /**
     * Whether the configured base URL points at Quiqup's staging estate.
     *
     * <p>Quiqup issues no self-serve sandbox key, so the only credential most accounts hold is a
     * live one and production is a single environment variable away. A create + ready_for_collection
     * against production dispatches a real courier to a real address, billed to the real account —
     * and our cancel contract is still unverified, so the undo is not guaranteed to work.
     */
    private boolean isStagingBaseUrl() {
        String base = props.getBaseUrl();
        return base != null && base.toLowerCase().contains("staging");
    }

    /**
     * Make an authenticated request to Quiqup. {@code path} may be a full URL or a path
     * relative to the configured base URL. Returns the status + body instead of throwing
     * on non-2xx, so the caller/UI sees the real Quiqup response.
     */
    public QuiqupApiResult request(String method, String path, JsonNode body) {
        log.info("[QUIQUP] {} {}", method, path);

        // Every caller — the admin endpoints and the raw tester alike — funnels through here, so
        // this is the one place the guard has to hold.
        QuiqupApiResult blocked = guardProductionWrite(method);
        if (blocked != null) return blocked;

        try {
            HttpMethod httpMethod = HttpMethod.valueOf(method.toUpperCase());
            Map<String, String> authHeaders = authHeaders(); // may throw if creds missing

            WebClient.RequestBodySpec spec = webClient.method(httpMethod).uri(path);
            authHeaders.forEach(spec::header);
            spec.header("Accept", MediaType.APPLICATION_JSON_VALUE);

            WebClient.RequestHeadersSpec<?> finalSpec = spec;
            if (body != null && !body.isNull() && httpMethod != HttpMethod.GET) {
                finalSpec = spec.contentType(MediaType.APPLICATION_JSON).bodyValue(body);
            }

            QuiqupApiResult result = finalSpec.exchangeToMono(resp ->
                    resp.bodyToMono(String.class).defaultIfEmpty("")
                            .map(raw -> new QuiqupApiResult(
                                    resp.statusCode().value(),
                                    resp.statusCode().is2xxSuccessful(),
                                    parse(raw))))
                    .timeout(Duration.ofMillis(props.getTimeoutMs()))
                    .block();
            if (result != null) {
                log.info("[QUIQUP] → {} ok={}", result.status(), result.ok());
            }
            return result;
        } catch (Exception e) {
            log.error("[QUIQUP] call failed {} {} — {}: {}", method, path,
                    e.getClass().getSimpleName(), e.getMessage(), e);
            return new QuiqupApiResult(502, false, describeFailure(method, path, e));
        }
    }

    /**
     * Turn a transport failure into something that says what to check next.
     *
     * <p>A bare {@code TimeoutException} is the least actionable outcome the admin page can show —
     * it looks identical whether the host is unreachable, the credentials are wrong, or Quiqup is
     * simply slow. Walking the cause chain lets the message name which one it was.
     */
    private String describeFailure(String method, String path, Exception e) {
        String url = props.getBaseUrl() + path;
        for (Throwable t = e; t != null; t = t.getCause()) {
            String type = t.getClass().getName();
            if (t instanceof java.net.UnknownHostException) {
                return "Cannot resolve the Quiqup host for " + url
                        + " — DNS failure from the app server. Check QUIQUP_BASE_URL and the server's resolver.";
            }
            if (type.contains("ConnectTimeout") || t instanceof java.net.ConnectException) {
                return "Could not open a connection to " + url + " within "
                        + props.getConnectTimeoutMs() + "ms. The app server most likely cannot reach "
                        + "Quiqup — outbound egress blocked, or Quiqup staging restricts by IP. "
                        + "Verify from the server itself: curl -sv " + url;
            }
        }
        if (e instanceof java.util.concurrent.TimeoutException
                || e.getCause() instanceof java.util.concurrent.TimeoutException) {
            return "Connected to " + url + " but Quiqup sent no response within "
                    + props.getTimeoutMs() + "ms. Raise QUIQUP_TIMEOUT_MS if their staging API is "
                    + "just slow; if it never answers, the request is likely being dropped rather "
                    + "than refused (an unauthenticated call to this URL normally returns 401 immediately).";
        }
        return "Quiqup call failed (" + method + " " + url + "): "
                + e.getClass().getSimpleName() + ": " + e.getMessage();
    }

    /**
     * Refuse anything that could mutate state on Quiqup's production estate.
     *
     * <p>Reads are always allowed — {@code GET /orders} is how you validate a token without side
     * effects. Writes against a non-staging base URL are refused unless
     * {@code QUIQUP_ALLOW_PRODUCTION_WRITES=true} is set as well, so going live is a deliberate
     * two-variable decision rather than a single-variable slip.
     *
     * @return a 409-style result to relay to the UI, or {@code null} when the call may proceed.
     */
    private QuiqupApiResult guardProductionWrite(String method) {
        boolean readOnly = "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
        if (readOnly || isStagingBaseUrl() || props.isAllowProductionWrites()) {
            return null;
        }
        log.warn("[QUIQUP] BLOCKED {} against non-staging base URL {} — set QUIQUP_ALLOW_PRODUCTION_WRITES=true to permit.",
                method, props.getBaseUrl());
        return new QuiqupApiResult(409, false,
                "Blocked: " + method + " is a write against a non-staging Quiqup base URL ("
                        + props.getBaseUrl() + "). Creating or dispatching an order here sends a real "
                        + "courier to a real address on the live account. Read-only calls (GET) are "
                        + "allowed. To permit writes deliberately, set QUIQUP_ALLOW_PRODUCTION_WRITES=true.");
    }

    /**
     * Confirm auth is usable. API-key mode just checks the key is present; OAuth mode
     * performs a real token exchange against staging.
     */
    public Map<String, Object> verify() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("authMode", props.getAuthMode());
        if ("oauth".equalsIgnoreCase(props.getAuthMode())) {
            try {
                String token = fetchOAuthToken(true);
                out.put("ok", token != null);
                out.put("tokenPreview", mask(token));
                out.put("expiresAt", tokenExpiry.toString());
            } catch (Exception e) {
                out.put("ok", false);
                out.put("error", e.getMessage());
            }
        } else {
            String key = props.getApiKey();
            out.put("ok", key != null && !key.isBlank());
            out.put("tokenPreview", mask(key));
            out.put("header", props.getApiKeyHeader());
        }
        return out;
    }

    // ── auth ────────────────────────────────────────────────────────────────

    private Map<String, String> authHeaders() {
        if ("oauth".equalsIgnoreCase(props.getAuthMode())) {
            return Map.of("Authorization", "Bearer " + fetchOAuthToken(false));
        }
        String key = props.getApiKey();
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("Quiqup API key is not configured (set QUIQUP_API_KEY).");
        }
        String prefix = props.getApiKeyPrefix();
        String value = (prefix == null || prefix.isBlank()) ? key : prefix + " " + key;
        return Map.of(props.getApiKeyHeader(), value);
    }

    private synchronized String fetchOAuthToken(boolean force) {
        if (!force && cachedToken != null && tokenExpiry.minusSeconds(30).isAfter(Instant.now())) {
            return cachedToken;
        }
        QuiqupProperties.Oauth o = props.getOauth();
        if (o.getClientId() == null || o.getClientSecret() == null) {
            throw new IllegalStateException("Quiqup OAuth client_id/client_secret not configured.");
        }
        BodyInserters.FormInserter<String> form = BodyInserters
                .fromFormData("grant_type", o.getGrantType())
                .with("client_id", o.getClientId())
                .with("client_secret", o.getClientSecret());
        if ("password".equalsIgnoreCase(o.getGrantType())) {
            form = form.with("username", o.getUsername()).with("password", o.getPassword());
        }
        if (o.getScope() != null && !o.getScope().isBlank()) {
            form = form.with("scope", o.getScope());
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> resp = webClient.post().uri(o.getTokenPath())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofMillis(props.getTimeoutMs()))
                .block();
        if (resp == null || resp.get("access_token") == null) {
            throw new IllegalStateException("Quiqup token response missing access_token");
        }
        cachedToken = String.valueOf(resp.get("access_token"));
        long expiresIn = resp.get("expires_in") != null ? Long.parseLong(String.valueOf(resp.get("expires_in"))) : 3600;
        tokenExpiry = Instant.now().plusSeconds(expiresIn);
        return cachedToken;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Object parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            return raw; // non-JSON (e.g. HTML error page) — return as-is for debugging
        }
    }

    private static String mask(String secret) {
        if (secret == null || secret.isBlank()) return null;
        if (secret.length() <= 12) return "***";
        return secret.substring(0, 6) + "…" + secret.substring(secret.length() - 4);
    }
}
