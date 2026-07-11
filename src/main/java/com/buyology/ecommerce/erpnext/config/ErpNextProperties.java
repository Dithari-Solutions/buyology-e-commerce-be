package com.buyology.ecommerce.erpnext.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for the ERPNext (Frappe) integration.
 *
 * <p><b>Initial / testing stage:</b> this module only <i>reads</i> the product (Item) list
 * from ERPNext and relays it to the admin dashboard. Nothing is persisted to our database.
 * It exists so admins can confirm connectivity against ERPNext before we build a real sync.
 *
 * <p>Auth uses Frappe's token scheme — every request carries
 * {@code Authorization: token <api-key>:<api-secret>} (see
 * <a href="https://docs.frappe.io/framework/user/en/api/rest">Frappe REST docs</a>).
 *
 * <p>Ships <b>inert</b>: with {@code erpnext.enabled=false} the admin endpoints return 409.
 * Flip {@code ERPNEXT_ENABLED=true} (+ base URL, key, secret) in the environment to test.
 */
@Component
@ConfigurationProperties(prefix = "erpnext")
public class ErpNextProperties {

    /** Master switch. When false the module is inert (no outbound calls). */
    private boolean enabled = false;

    /** ERPNext site base URL, e.g. {@code https://your-site.frappe.cloud} (no trailing slash). */
    private String baseUrl;

    /** Frappe API key (the public half of the token pair). */
    private String apiKey;

    /** Frappe API secret (the private half of the token pair). */
    private String apiSecret;

    /** Timeout for outbound ERPNext calls, milliseconds. */
    private long timeoutMs = 10000;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getApiSecret() { return apiSecret; }
    public void setApiSecret(String apiSecret) { this.apiSecret = apiSecret; }
    public long getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
}
