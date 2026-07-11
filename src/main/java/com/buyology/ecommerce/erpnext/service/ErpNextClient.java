package com.buyology.ecommerce.erpnext.service;

import com.buyology.ecommerce.erpnext.config.ErpNextProperties;
import com.buyology.ecommerce.erpnext.dto.ErpProduct;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Thin authenticated read client for the ERPNext (Frappe) REST API.
 *
 * <p>Self-contained (builds its own {@link WebClient} from {@link ErpNextProperties#getBaseUrl()},
 * mirroring the Quiqup client) so it depends on no shared bean. Only performs GET reads against
 * the {@code Item} doctype — it never writes to ERPNext or to our database.
 *
 * <p>Auth: Frappe token scheme — {@code Authorization: token <api-key>:<api-secret>}.
 */
@Component
public class ErpNextClient {

    private static final Logger log = LoggerFactory.getLogger(ErpNextClient.class);

    /** Item fields we request from ERPNext (kept small for the testing projection). */
    private static final List<String> ITEM_FIELDS = List.of(
            "name", "item_code", "item_name", "description",
            "item_group", "brand", "standard_rate", "stock_uom", "image", "disabled");

    private final ErpNextProperties props;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public ErpNextClient(ErpNextProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        // baseUrl may be null when the module is disabled; WebClient is only exercised once enabled.
        this.webClient = WebClient.builder().build();
    }

    /**
     * Fetch up to {@code limit} products (Items) from ERPNext. Returns them mapped to
     * {@link ErpProduct}. Throws {@link ErpNextException} on a non-2xx response or transport
     * failure so the controller can relay a meaningful message to the admin UI.
     */
    public List<ErpProduct> listProducts(int limit) {
        requireConfigured();
        String fieldsJson = writeFields();
        URI uri = UriComponentsBuilder
                .fromUriString(props.getBaseUrl())
                .path("/api/resource/Item")
                .queryParam("fields", fieldsJson)
                .queryParam("limit_page_length", limit)
                .queryParam("order_by", "modified desc")
                .build()
                .encode()
                .toUri();

        log.info("[ERPNEXT] GET {}", uri);
        try {
            String raw = webClient.get()
                    .uri(uri)
                    .header("Authorization", tokenHeader())
                    .accept(MediaType.APPLICATION_JSON)
                    .exchangeToMono(resp -> resp.bodyToMono(String.class).defaultIfEmpty("")
                            .map(body -> {
                                if (!resp.statusCode().is2xxSuccessful()) {
                                    throw new ErpNextException(
                                            "ERPNext returned HTTP " + resp.statusCode().value() + ": " + snippet(body));
                                }
                                return body;
                            }))
                    .timeout(Duration.ofMillis(props.getTimeoutMs()))
                    .block();

            return parseItems(raw);
        } catch (ErpNextException e) {
            throw e;
        } catch (Exception e) {
            log.error("[ERPNEXT] product list failed — {}", e.getMessage());
            throw new ErpNextException("ERPNext call failed (" + props.getBaseUrl() + "): " + e.getMessage(), e);
        }
    }

    /** Confirm credentials + base URL are usable by fetching a single Item. */
    public boolean verify() {
        listProducts(1);
        return true;
    }

    // ── mapping ───────────────────────────────────────────────────────────────

    private List<ErpProduct> parseItems(String raw) {
        List<ErpProduct> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) return out;
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode data = root.path("data");
            if (data.isArray()) {
                for (JsonNode item : data) {
                    out.add(mapItem(item));
                }
            }
        } catch (Exception e) {
            throw new ErpNextException("Could not parse ERPNext response: " + e.getMessage(), e);
        }
        return out;
    }

    private ErpProduct mapItem(JsonNode n) {
        return new ErpProduct(
                text(n, "name"),
                text(n, "item_code"),
                text(n, "item_name"),
                text(n, "description"),
                text(n, "item_group"),
                text(n, "brand"),
                n.hasNonNull("standard_rate") ? n.get("standard_rate").asDouble() : null,
                text(n, "stock_uom"),
                absoluteImage(text(n, "image")),
                n.hasNonNull("disabled") ? n.get("disabled").asInt() != 0 : null);
    }

    /** Frappe returns image as a site-relative path (e.g. {@code /files/x.png}); make it absolute. */
    private String absoluteImage(String image) {
        if (image == null || image.isBlank()) return null;
        if (image.startsWith("http://") || image.startsWith("https://")) return image;
        String base = props.getBaseUrl().replaceAll("/$", "");
        return base + (image.startsWith("/") ? image : "/" + image);
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    // ── auth / config ───────────────────────────────────────────────────────────

    private String tokenHeader() {
        return "token " + props.getApiKey() + ":" + props.getApiSecret();
    }

    private void requireConfigured() {
        if (props.getBaseUrl() == null || props.getBaseUrl().isBlank()) {
            throw new ErpNextException("ERPNext base URL is not configured (set ERPNEXT_BASE_URL).");
        }
        if (props.getApiKey() == null || props.getApiKey().isBlank()
                || props.getApiSecret() == null || props.getApiSecret().isBlank()) {
            throw new ErpNextException("ERPNext credentials are not configured (set ERPNEXT_API_KEY / ERPNEXT_API_SECRET).");
        }
    }

    private String writeFields() {
        try {
            return objectMapper.writeValueAsString(ITEM_FIELDS);
        } catch (Exception e) {
            // ITEM_FIELDS is a constant list of strings — serialization cannot realistically fail.
            return "[\"name\",\"item_code\",\"item_name\"]";
        }
    }

    private static String snippet(String body) {
        if (body == null) return "";
        String s = body.strip();
        return s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }

    /** Raised when ERPNext is misconfigured or returns an error; carries a UI-friendly message. */
    public static class ErpNextException extends RuntimeException {
        public ErpNextException(String message) { super(message); }
        public ErpNextException(String message, Throwable cause) { super(message, cause); }
    }
}
