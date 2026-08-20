package com.buyology.ecommerce.quiqup.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for the Quiqup delivery integration (TEST/STAGING module).
 *
 * <p>This module is deliberately decoupled from the order lifecycle — it exists so
 * admins can exercise the Quiqup <b>staging</b> API (create a mock delivery, track,
 * cancel, observe webhooks) without any impact on production orders. It ships
 * <b>inert</b>: with {@code quiqup.enabled=false} every admin action returns 409 and
 * webhooks are ignored. Flip {@code QUIQUP_ENABLED=true} (+ {@code QUIQUP_API_KEY}) in
 * the environment when you are ready to test.
 *
 * <p>Base URL defaults to Quiqup <b>staging</b>. Only set it to {@code api-ae.quiqup.com}
 * once a real integration is signed off.
 */
@Component
@ConfigurationProperties(prefix = "quiqup")
public class QuiqupProperties {

    /**
     * Refuse to start with signature enforcement switched on and no secret to enforce with.
     *
     * <p>That combination used to be the most dangerous configuration available: verification
     * cannot run without a secret, so every delivery — forged ones included — was accepted, while
     * the admin config page reported enforcement as on. Failing at startup makes the
     * misconfiguration loud, in the same spirit as the JWT-secret and CORS-allowlist checks. Only
     * checked when the module is enabled, so a deployment with Quiqup switched off is unaffected.
     */
    @jakarta.annotation.PostConstruct
    void validate() {
        if (enabled && dispatch.isEnabled() && !isStagingBase() && !allowProductionWrites) {
            throw new IllegalStateException(
                    "quiqup.dispatch.enabled is true against a non-staging base URL (" + baseUrl
                    + ") while quiqup.allow-production-writes is false. Every dispatch is a write, "
                    + "so dispatch would fail on every order and paid orders would silently pile up "
                    + "undelivered. Set QUIQUP_ALLOW_PRODUCTION_WRITES=true to dispatch for real, or "
                    + "QUIQUP_DISPATCH_ENABLED=false to leave dispatch off.");
        }
        if (enabled && webhookRequireSignature && (webhookSecret == null || webhookSecret.isBlank())) {
            throw new IllegalStateException(
                    "quiqup.webhook-require-signature is true but quiqup.webhook-secret is blank. "
                    + "Signature verification cannot run without the secret, so enforcement would "
                    + "silently accept every webhook. Set QUIQUP_WEBHOOK_SECRET (from GET "
                    + "/subscriptions/{id}/secret on the subscription for THIS environment), or set "
                    + "QUIQUP_WEBHOOK_REQUIRE_SIGNATURE=false until you have it.");
        }
    }

    /** Master switch. When false the module is inert (no outbound calls, webhooks ignored). */
    private boolean enabled = false;

    /** Quiqup API base URL. Defaults to staging. */
    private String baseUrl = "https://api.staging.quiqup.com";

    /** Auth mode: "apikey" (static key header) or "oauth" (client-credentials/password token). */
    private String authMode = "apikey";

    // ── API-key auth ─────────────────────────────────────────────────────────
    private String apiKey;
    /** Header the key is sent in. Default sends {@code Authorization: Bearer <key>}. */
    private String apiKeyHeader = "Authorization";
    /** Prefix before the key ("Bearer"); blank to send the raw key. */
    private String apiKeyPrefix = "Bearer";
    /** Optional account/partner identifier some payloads require. */
    private String accountId;

    /**
     * Secret Quiqup signs webhooks with, from {@code GET /subscriptions/{id}/secret} on the
     * subscription that fires the deliveries.
     *
     * <p>Per-environment: a staging subscription's secret will not verify production deliveries or
     * vice versa, because the subscriptions themselves are per-environment.
     */
    private String webhookSecret;

    /**
     * Reject a delivery whose signature does not verify, instead of only recording the result.
     *
     * <p>Defaults to false so signature verification can be observed against real deliveries before
     * it is allowed to drop them. Flip it to true once the admin Webhooks tab shows deliveries
     * arriving with a valid signature; until at least one has verified, a true here would silently
     * discard every real event.
     */
    private boolean webhookRequireSignature = false;

    /**
     * Shared secret sent back to us in a custom header on every webhook delivery, configured on the
     * Quiqup subscription's "custom headers" field.
     *
     * <p>Independent of {@link #webhookSecret}: their HMAC scheme is undocumented (no header name,
     * algorithm variant or encoding published), whereas a header we choose the value of is
     * deterministic and verifiable today. Checked in addition to the HMAC, never instead of it.
     */
    private String webhookToken;

    /** Header carrying {@link #webhookToken}. Must match what the Quiqup subscription sends. */
    private String webhookTokenHeader = "X-Buyology-Webhook-Token";

    /**
     * Permit state-changing calls when {@link #baseUrl} is not Quiqup staging.
     *
     * <p>Quiqup issues no self-serve sandbox credential, so the key most accounts hold is a live
     * one and production sits one environment variable away. Creating an order and marking it ready
     * for collection dispatches a real courier to a real address, billed to the real account, and
     * our cancel contract is still unverified — so the undo is not guaranteed. Reads are never
     * blocked; only writes, and only off staging. Turning this on is the deliberate second key.
     */
    private boolean allowProductionWrites = false;

    /** Response timeout for outbound Quiqup calls, milliseconds — time to a reply once connected. */
    private long timeoutMs = 20000;

    /**
     * TCP connect timeout, milliseconds. Deliberately much shorter than {@link #timeoutMs} so an
     * unreachable host (blocked egress, DNS failure) fails fast and distinctly, instead of looking
     * identical to a slow-but-reachable API.
     */
    private long connectTimeoutMs = 5000;

    private final Oauth oauth = new Oauth();
    private final Paths paths = new Paths();
    private final Dispatch dispatch = new Dispatch();

    /**
     * Automatic dispatch of paid orders to Quiqup.
     *
     * <p>Separate from {@link QuiqupProperties#enabled} on purpose. The module being on means an
     * admin may drive the API by hand; dispatch being on means paid customer orders leave for a
     * real courier without anyone watching. Those are different decisions and want different
     * switches — turning the module on to check connectivity should not start dispatching.
     */
    public static class Dispatch {

        /** Master switch for automatic dispatch. Off by default; the module ships inert. */
        private boolean enabled = false;

        /**
         * Quiqup service level for a standard delivery. "partner_next_day" matches the ecommerce
         * preset; "partner_4hr" is the on-demand service. Configurable because it is a commercial
         * choice, not a technical one.
         */
        private String kind = "partner_next_day";

        /**
         * Whether to mark the job ready for collection immediately after creating it.
         *
         * <p>Off by default, and the default is the safe one: ready-for-collection is what actually
         * sends a courier to the shop. Leaving it off means jobs queue at Quiqup for a human to
         * release once the parcel is physically packed, which is the correct sequence for a
         * warehouse that has not yet picked the items.
         */
        private boolean autoReadyForCollection = false;

        /** How long a paid order may go undispatched before the retry job picks it up, minutes. */
        private int retryAfterMinutes = 10;

        /** Give up after this many attempts, so a permanently unmappable order stops being retried. */
        private int maxAttempts = 5;

        /** Orders paid longer ago than this are never auto-dispatched — see the retry job. */
        private int retryHorizonHours = 48;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getKind() { return kind; }
        public void setKind(String kind) { this.kind = kind; }
        public boolean isAutoReadyForCollection() { return autoReadyForCollection; }
        public void setAutoReadyForCollection(boolean v) { this.autoReadyForCollection = v; }
        public int getRetryAfterMinutes() { return retryAfterMinutes; }
        public void setRetryAfterMinutes(int v) { this.retryAfterMinutes = v; }
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int v) { this.maxAttempts = v; }
        public int getRetryHorizonHours() { return retryHorizonHours; }
        public void setRetryHorizonHours(int v) { this.retryHorizonHours = v; }
    }

    public Dispatch getDispatch() { return dispatch; }

    /** Whether {@link #baseUrl} points at Quiqup staging. Mirrors the client's own check. */
    public boolean isStagingBase() {
        return baseUrl != null && baseUrl.toLowerCase(java.util.Locale.ROOT).contains("staging");
    }

    /** OAuth settings (used only when authMode=oauth). */
    public static class Oauth {
        private String clientId;
        private String clientSecret;
        private String username;
        private String password;
        private String grantType = "client_credentials";
        private String scope;
        private String tokenPath = "/oauth/token";

        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        public String getClientSecret() { return clientSecret; }
        public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getGrantType() { return grantType; }
        public void setGrantType(String grantType) { this.grantType = grantType; }
        public String getScope() { return scope; }
        public void setScope(String scope) { this.scope = scope; }
        public String getTokenPath() { return tokenPath; }
        public void setTokenPath(String tokenPath) { this.tokenPath = tokenPath; }
    }

    /**
     * Quiqup order endpoint templates (unified /orders API — confirmed against the live
     * staging docs). Overridable via config; {@code {id}} is filled at call time.
     */
    public static class Paths {
        private String create = "/orders";                                       // POST
        private String get = "/orders/{id}";                                     // GET
        private String list = "/orders";                                         // GET
        private String update = "/orders/{id}";                                  // PUT (pending orders)
        private String readyForCollection = "/orders/{id}/ready_for_collection"; // PUT — triggers pickup
        private String cancel = "/orders/batch/set_cancelled";                   // PUT — id(s) in body
        private String label = "/order_label/{id}";                              // GET — AWB document
        private String addParcel = "/orders/{id}/parcels";                       // POST

        public String getCreate() { return create; }
        public void setCreate(String v) { this.create = v; }
        public String getGet() { return get; }
        public void setGet(String v) { this.get = v; }
        public String getList() { return list; }
        public void setList(String v) { this.list = v; }
        public String getUpdate() { return update; }
        public void setUpdate(String v) { this.update = v; }
        public String getReadyForCollection() { return readyForCollection; }
        public void setReadyForCollection(String v) { this.readyForCollection = v; }
        public String getCancel() { return cancel; }
        public void setCancel(String v) { this.cancel = v; }
        public String getLabel() { return label; }
        public void setLabel(String v) { this.label = v; }
        public String getAddParcel() { return addParcel; }
        public void setAddParcel(String v) { this.addParcel = v; }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getAuthMode() { return authMode; }
    public void setAuthMode(String authMode) { this.authMode = authMode; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getApiKeyHeader() { return apiKeyHeader; }
    public void setApiKeyHeader(String apiKeyHeader) { this.apiKeyHeader = apiKeyHeader; }
    public String getApiKeyPrefix() { return apiKeyPrefix; }
    public void setApiKeyPrefix(String apiKeyPrefix) { this.apiKeyPrefix = apiKeyPrefix; }
    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public String getWebhookSecret() { return webhookSecret; }
    public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }
    public boolean isWebhookRequireSignature() { return webhookRequireSignature; }
    public void setWebhookRequireSignature(boolean webhookRequireSignature) {
        this.webhookRequireSignature = webhookRequireSignature;
    }
    public String getWebhookToken() { return webhookToken; }
    public void setWebhookToken(String webhookToken) { this.webhookToken = webhookToken; }
    public String getWebhookTokenHeader() { return webhookTokenHeader; }
    public void setWebhookTokenHeader(String webhookTokenHeader) { this.webhookTokenHeader = webhookTokenHeader; }
    public boolean isAllowProductionWrites() { return allowProductionWrites; }
    public void setAllowProductionWrites(boolean allowProductionWrites) { this.allowProductionWrites = allowProductionWrites; }
    public long getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
    public long getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(long connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
    public Oauth getOauth() { return oauth; }
    public Paths getPaths() { return paths; }
}
