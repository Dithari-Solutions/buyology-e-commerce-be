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

    // ── order → ERPNext push ──────────────────────────────────────────────────

    /**
     * Push a Sales Order + Sales Invoice to ERPNext when an order is PAID.
     * Independent of {@link #enabled}: both must be true for anything to be sent.
     */
    private boolean syncOrders = true;

    /**
     * Submit the created documents ({@code docstatus=1}) instead of leaving them as drafts.
     * Submitted invoices post GL entries; set false to review them in ERPNext first.
     */
    private boolean submitDocuments = true;

    /** ERPNext Company the documents belong to. Blank = let ERPNext apply its default. */
    private String company;

    /**
     * Create the ERPNext Customer on the fly when the buyer has no matching record.
     * With this false, an order for an unknown customer fails the sync (and is recorded).
     */
    private boolean autoCreateCustomer = true;

    /**
     * Create a minimal ERPNext Item when an ordered SKU does not exist there yet.
     * ERPNext rejects a Sales Order referencing an unknown item_code, so with this false
     * any order containing a not-yet-synced product fails the sync.
     */
    private boolean autoCreateItems = true;

    /** Item Group assigned to auto-created Items. Must exist in ERPNext. */
    private String defaultItemGroup = "Products";

    /** Unit of measure assigned to auto-created Items. Must exist in ERPNext. */
    private String defaultUom = "Nos";

    /** Customer Group assigned to auto-created Customers. Must exist in ERPNext. */
    private String customerGroup = "All Customer Groups";

    /** Territory assigned to auto-created Customers. Must exist in ERPNext. */
    private String territory = "All Territories";

    /**
     * Account head used for the shipping charge row (e.g. {@code "Freight and Forwarding
     * Charges - B"}). Blank = shipping is omitted from the ERPNext documents, in which case
     * the ERPNext totals are the goods total only. Account names are company-specific.
     */
    private String shippingAccountHead;

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

    public boolean isSyncOrders() { return syncOrders; }
    public void setSyncOrders(boolean syncOrders) { this.syncOrders = syncOrders; }
    public boolean isSubmitDocuments() { return submitDocuments; }
    public void setSubmitDocuments(boolean submitDocuments) { this.submitDocuments = submitDocuments; }
    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }
    public boolean isAutoCreateCustomer() { return autoCreateCustomer; }
    public void setAutoCreateCustomer(boolean autoCreateCustomer) { this.autoCreateCustomer = autoCreateCustomer; }
    public boolean isAutoCreateItems() { return autoCreateItems; }
    public void setAutoCreateItems(boolean autoCreateItems) { this.autoCreateItems = autoCreateItems; }
    public String getDefaultItemGroup() { return defaultItemGroup; }
    public void setDefaultItemGroup(String defaultItemGroup) { this.defaultItemGroup = defaultItemGroup; }
    public String getDefaultUom() { return defaultUom; }
    public void setDefaultUom(String defaultUom) { this.defaultUom = defaultUom; }
    public String getCustomerGroup() { return customerGroup; }
    public void setCustomerGroup(String customerGroup) { this.customerGroup = customerGroup; }
    public String getTerritory() { return territory; }
    public void setTerritory(String territory) { this.territory = territory; }
    public String getShippingAccountHead() { return shippingAccountHead; }
    public void setShippingAccountHead(String shippingAccountHead) { this.shippingAccountHead = shippingAccountHead; }
}
