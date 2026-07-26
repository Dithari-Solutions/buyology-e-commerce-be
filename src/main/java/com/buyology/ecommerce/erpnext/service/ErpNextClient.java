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
        return listProducts(limit, 0);
    }

    /**
     * Paginated variant: fetch {@code limit} products starting at {@code offset}
     * (Frappe {@code limit_start}). Used by the catalog import to page through the
     * whole Item list.
     */
    public List<ErpProduct> listProducts(int limit, int offset) {
        requireConfigured();
        URI uri = UriComponentsBuilder
                .fromUriString(props.getBaseUrl())
                .path("/api/resource/Item")
                .queryParam("fields", writeFields())
                .queryParam("limit_page_length", limit)
                .queryParam("limit_start", Math.max(0, offset))
                .queryParam("order_by", "modified desc")
                .build()
                .encode()
                .toUri();

        log.info("[ERPNEXT] GET {}", uri);
        JsonNode root = exchange("GET", uri, null);
        return parseItems(root);
    }

    /**
     * Fetch the full projection for a specific set of {@code item_code}s (one request).
     * Used by the import to resolve the items the admin selected in the preview.
     */
    public List<ErpProduct> getItemsByCode(java.util.Collection<String> itemCodes) {
        requireConfigured();
        if (itemCodes == null || itemCodes.isEmpty()) return new ArrayList<>();

        StringBuilder inList = new StringBuilder("[");
        boolean first = true;
        for (String code : itemCodes) {
            if (!first) inList.append(",");
            inList.append(quote(code));
            first = false;
        }
        inList.append("]");

        URI uri = UriComponentsBuilder
                .fromUriString(props.getBaseUrl())
                .path("/api/resource/Item")
                .queryParam("filters", "[[\"item_code\",\"in\"," + inList + "]]")
                .queryParam("fields", writeFields())
                .queryParam("limit_page_length", 0)
                .build()
                .encode()
                .toUri();

        log.info("[ERPNEXT] GET {}", uri);
        return parseItems(exchange("GET", uri, null));
    }

    /**
     * On-hand quantity per {@code item_code}, summed across every warehouse
     * ({@code Bin.actual_qty}). Item codes not present in any Bin are absent from the
     * map (treat as 0). One request for the whole batch.
     */
    public java.util.Map<String, Double> stockByItemCode(java.util.Collection<String> itemCodes) {
        requireConfigured();
        java.util.Map<String, Double> out = new java.util.LinkedHashMap<>();
        if (itemCodes == null || itemCodes.isEmpty()) return out;

        StringBuilder inList = new StringBuilder("[");
        boolean first = true;
        for (String code : itemCodes) {
            if (!first) inList.append(",");
            inList.append(quote(code));
            first = false;
        }
        inList.append("]");
        String filters = "[[\"item_code\",\"in\"," + inList + "]]";

        URI uri = UriComponentsBuilder
                .fromUriString(props.getBaseUrl())
                .path("/api/resource/Bin")
                .queryParam("filters", filters)
                .queryParam("fields", "[\"item_code\",\"actual_qty\"]")
                .queryParam("limit_page_length", 0) // 0 = no limit (all matching bins)
                .build()
                .encode()
                .toUri();

        log.info("[ERPNEXT] GET {}", uri);
        JsonNode data = exchange("GET", uri, null).path("data");
        if (data.isArray()) {
            for (JsonNode bin : data) {
                String code = text(bin, "item_code");
                if (code == null) continue;
                double qty = bin.hasNonNull("actual_qty") ? bin.get("actual_qty").asDouble() : 0d;
                out.merge(code, qty, Double::sum);
            }
        }
        return out;
    }

    /** Confirm credentials + base URL are usable by fetching a single Item. */
    public boolean verify() {
        listProducts(1);
        return true;
    }

    // ── generic document API (used by the order → ERPNext push) ────────────────

    /**
     * Create a document: {@code POST /api/resource/{doctype}}.
     *
     * <p>Set {@code docstatus: 1} inside {@code body} to create it already submitted
     * (ERPNext posts GL entries only for submitted documents).
     *
     * @return the {@code data} node of the response (the created document)
     */
    public JsonNode createDocument(String doctype, JsonNode body) {
        requireConfigured();
        URI uri = resourceUri(doctype, null);
        log.info("[ERPNEXT] POST {}", uri);
        JsonNode response = exchange("POST", uri, body);
        return response.path("data");
    }

    /**
     * Find the {@code name} (primary key) of the first document matching {@code filters},
     * or null when nothing matches. {@code filters} uses Frappe's list syntax, e.g.
     * {@code [["item_code","=","ABC"]]}.
     */
    public String findDocumentName(String doctype, String filtersJson) {
        requireConfigured();
        URI uri = UriComponentsBuilder
                .fromUriString(props.getBaseUrl())
                .path("/api/resource/" + doctype)
                .queryParam("filters", filtersJson)
                .queryParam("fields", "[\"name\"]")
                .queryParam("limit_page_length", 1)
                .build()
                .encode()
                .toUri();
        log.info("[ERPNEXT] GET {}", uri);
        JsonNode data = exchange("GET", uri, null).path("data");
        if (data.isArray() && !data.isEmpty()) {
            JsonNode name = data.get(0).get("name");
            return name == null || name.isNull() ? null : name.asText();
        }
        return null;
    }

    /** True when a document with this exact name exists. */
    public boolean documentExists(String doctype, String name) {
        return findDocumentName(doctype, "[[\"name\",\"=\"," + quote(name) + "]]") != null;
    }

    /** Absolute URL of a document in the ERPNext desk UI, for admin deep-links. */
    public String deskUrl(String doctype, String name) {
        if (name == null || props.getBaseUrl() == null) return null;
        String slug = doctype.toLowerCase().replace(' ', '-');
        return props.getBaseUrl().replaceAll("/$", "") + "/app/" + slug + "/" + name;
    }

    // ── transport ─────────────────────────────────────────────────────────────

    private URI resourceUri(String doctype, String name) {
        UriComponentsBuilder b = UriComponentsBuilder
                .fromUriString(props.getBaseUrl())
                .path("/api/resource/" + doctype);
        if (name != null) b.path("/" + name);
        return b.build().encode().toUri();
    }

    /**
     * Perform a request and return the parsed JSON body, raising {@link ErpNextException}
     * with the ERPNext error text on any non-2xx response.
     */
    private JsonNode exchange(String method, URI uri, JsonNode body) {
        try {
            WebClient.RequestBodySpec spec = webClient
                    .method(org.springframework.http.HttpMethod.valueOf(method))
                    .uri(uri)
                    .header("Authorization", tokenHeader())
                    .accept(MediaType.APPLICATION_JSON);

            WebClient.RequestHeadersSpec<?> finalSpec = body == null
                    ? spec
                    : spec.contentType(MediaType.APPLICATION_JSON).bodyValue(body);

            String raw = finalSpec
                    .exchangeToMono(resp -> resp.bodyToMono(String.class).defaultIfEmpty("")
                            .map(payload -> {
                                if (!resp.statusCode().is2xxSuccessful()) {
                                    throw new ErpNextException("ERPNext returned HTTP "
                                            + resp.statusCode().value() + ": " + errorText(payload));
                                }
                                return payload;
                            }))
                    .timeout(Duration.ofMillis(props.getTimeoutMs()))
                    .block();

            if (raw == null || raw.isBlank()) return objectMapper.createObjectNode();
            return objectMapper.readTree(raw);
        } catch (ErpNextException e) {
            throw e;
        } catch (Exception e) {
            throw new ErpNextException("ERPNext call failed (" + uri + "): " + e.getMessage(), e);
        }
    }

    /**
     * Pull the useful message out of a Frappe error body. Frappe returns a JSON envelope
     * with {@code exception} / {@code _server_messages} and a full HTML traceback, which is
     * useless in a UI — prefer the concise fields when present.
     */
    private String errorText(String payload) {
        if (payload == null || payload.isBlank()) return "(empty response)";
        try {
            JsonNode root = objectMapper.readTree(payload);
            for (String field : new String[]{"exception", "message", "_error_message"}) {
                JsonNode n = root.get(field);
                if (n != null && !n.isNull() && !n.asText().isBlank()) return snippet(n.asText());
            }
            JsonNode serverMessages = root.get("_server_messages");
            if (serverMessages != null && !serverMessages.isNull()) {
                // _server_messages is a JSON-encoded array of JSON-encoded objects.
                JsonNode arr = objectMapper.readTree(serverMessages.asText());
                if (arr.isArray() && !arr.isEmpty()) {
                    JsonNode first = objectMapper.readTree(arr.get(0).asText());
                    JsonNode msg = first.get("message");
                    if (msg != null && !msg.isNull()) return snippet(msg.asText());
                }
            }
        } catch (Exception ignored) {
            // Not JSON (often an HTML error page) — fall through to the raw snippet.
        }
        return snippet(payload);
    }

    /** JSON-quote a value for inline use in a Frappe filters string. */
    public static String quote(String value) {
        return "\"" + (value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"")) + "\"";
    }

    // ── mapping ───────────────────────────────────────────────────────────────

    private List<ErpProduct> parseItems(JsonNode root) {
        List<ErpProduct> out = new ArrayList<>();
        if (root == null) return out;
        JsonNode data = root.path("data");
        if (data.isArray()) {
            for (JsonNode item : data) {
                out.add(mapItem(item));
            }
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
