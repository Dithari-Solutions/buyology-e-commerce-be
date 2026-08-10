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

    /** Optional shared secret Quiqup signs webhooks with (HMAC), if configured. */
    private String webhookSecret;

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

    /** Timeout for outbound Quiqup calls, milliseconds. */
    private long timeoutMs = 10000;

    private final Oauth oauth = new Oauth();
    private final Paths paths = new Paths();

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
    public boolean isAllowProductionWrites() { return allowProductionWrites; }
    public void setAllowProductionWrites(boolean allowProductionWrites) { this.allowProductionWrites = allowProductionWrites; }
    public long getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
    public Oauth getOauth() { return oauth; }
    public Paths getPaths() { return paths; }
}
