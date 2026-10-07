package com.buyology.ecommerce.cart.admin;

import com.buyology.ecommerce.common.service.EmailService;
import com.buyology.ecommerce.customeremail.service.CustomerEmailGuard;
import com.buyology.ecommerce.customeremail.service.MarketingAudience;
import com.buyology.ecommerce.infrastructure.external.ContaboObjectService;
import com.buyology.ecommerce.notification.service.PushNotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.util.*;

@Service
public class AdminCartActivityService {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    private final ContaboObjectService objects;
    private final EmailService email;
    private final PushNotificationService push;
    private final CustomerEmailGuard guard;
    private final String baseUrl;

    public AdminCartActivityService(JdbcTemplate jdbc, ContaboObjectService objects, EmailService email,
                                   PushNotificationService push, CustomerEmailGuard guard,
                                   @Value("${app.base-url:https://buyology.online}") String baseUrl) {
        this.jdbc = jdbc;
        this.named = new NamedParameterJdbcTemplate(jdbc);
        this.objects = objects;
        this.email = email;
        this.push = push;
        this.guard = guard;
        this.baseUrl = baseUrl.replaceAll("/$", "");
    }

    private static final String CUSTOMER_FROM = """
        FROM users u
        LEFT JOIN LATERAL (
            SELECT ac.email, COALESCE(NULLIF(ac.phone_number, ''),
                (SELECT phone.phone_number FROM auth_credentials phone WHERE phone.user_id = u.id AND NULLIF(phone.phone_number, '') IS NOT NULL ORDER BY phone.is_active DESC, phone.created_at, phone.id LIMIT 1)) AS phone_number
            FROM auth_credentials ac WHERE ac.user_id = u.id
            ORDER BY ac.is_active DESC NULLS LAST, (NULLIF(ac.email, '') IS NOT NULL) DESC, (ac.provider = 'EMAIL') DESC, ac.created_at, ac.id LIMIT 1
        ) contact ON TRUE
        LEFT JOIN LATERAL (
            SELECT MAX(c.last_added_at) AS last_added_at FROM carts c
            JOIN auth_credentials ac ON ac.id = c.auth_credential_id WHERE ac.user_id = u.id
        ) activity ON TRUE
        WHERE u.user_type = 'CUSTOMER' AND u.deleted_at IS NULL
        """;
    private static final String CUSTOMER_SELECT = """
        SELECT u.id AS user_id, u.first_name, u.last_name, u.status,
               contact.email, contact.phone_number, activity.last_added_at,
               (SELECT COUNT(*) FROM cart_items ci JOIN carts c ON c.id = ci.cart_id
                JOIN auth_credentials ac ON ac.id = c.auth_credential_id
                WHERE ac.user_id = u.id AND c.id = (SELECT current_cart.id FROM carts current_cart WHERE current_cart.auth_credential_id = c.auth_credential_id AND current_cart.status IN ('ACTIVE', 'CHECKED_OUT') ORDER BY (current_cart.status = 'ACTIVE') DESC, current_cart.updated_at DESC, current_cart.id LIMIT 1)) AS item_count
        """;
    private static final String SEARCH = """
         AND (LOWER(CONCAT_WS(' ', u.first_name, u.last_name)) LIKE :search
           OR EXISTS (SELECT 1 FROM auth_credentials ac WHERE ac.user_id = u.id
                      AND (LOWER(ac.email) LIKE :search OR ac.phone_number LIKE :search)))
        """;
    // One batch for previews; full detail is restricted to one customer. No customer-cart writes.
    private static final String ITEMS = """
        SELECT ci.id, ac.user_id, c.id AS cart_id, c.currency, c.country_code,
               ci.product_id, ci.variant_id, p.sku, pv.sku AS variant_sku,
               COALESCE(pt.title, p.sku) AS title,
               media.url AS image_url, ci.quantity, ci.selected, ci.unit_price, ci.total_price,
               ci.created_at, ci.updated_at,
               ROW_NUMBER() OVER (PARTITION BY ac.user_id ORDER BY ci.created_at DESC, ci.id) AS rn
        FROM cart_items ci JOIN carts c ON c.id = ci.cart_id
        JOIN auth_credentials ac ON ac.id = c.auth_credential_id
        JOIN products p ON p.id = ci.product_id
        LEFT JOIN product_variants pv ON pv.id = ci.variant_id
        LEFT JOIN product_translations pt ON pt.product_id = p.id AND pt.language = 'EN'
        LEFT JOIN LATERAL (SELECT pm.url FROM product_media pm
             WHERE pm.product_id = p.id AND pm.media_type = 'IMAGE'
             ORDER BY pm.is_primary DESC, pm.order_index, pm.id LIMIT 1) media ON TRUE
        WHERE c.id = (SELECT current_cart.id FROM carts current_cart WHERE current_cart.auth_credential_id = c.auth_credential_id AND current_cart.status IN ('ACTIVE', 'CHECKED_OUT') ORDER BY (current_cart.status = 'ACTIVE') DESC, current_cart.updated_at DESC, current_cart.id LIMIT 1) AND ac.user_id IN (:ids)
        """;

    public Map<String, Object> list(int page, int size, String search, boolean withItems) {
        String filter = (search == null || search.isBlank()) ? "" : SEARCH;
        String havingItems = withItems ? " AND EXISTS (SELECT 1 FROM carts c JOIN auth_credentials ac ON ac.id = c.auth_credential_id JOIN cart_items ci ON ci.cart_id = c.id WHERE ac.user_id = u.id AND c.id = (SELECT current_cart.id FROM carts current_cart WHERE current_cart.auth_credential_id = c.auth_credential_id AND current_cart.status IN ('ACTIVE', 'CHECKED_OUT') ORDER BY (current_cart.status = 'ACTIVE') DESC, current_cart.updated_at DESC, current_cart.id LIMIT 1))" : "";
        Map<String, Object> params = new HashMap<>();
        params.put("search", "%" + (search == null ? "" : search.trim().toLowerCase(Locale.ROOT)) + "%");
        params.put("limit", size); params.put("offset", (long) page * size);
        Long total = named.queryForObject("SELECT COUNT(*) " + CUSTOMER_FROM + filter + havingItems, params, Long.class);
        List<Map<String, Object>> customers = named.queryForList(CUSTOMER_SELECT + CUSTOMER_FROM + filter + havingItems
                + " ORDER BY activity.last_added_at DESC NULLS LAST, u.created_at DESC, u.id LIMIT :limit OFFSET :offset", params);
        if (!customers.isEmpty()) {
            List<Object> ids = customers.stream().map(row -> row.get("user_id")).toList();
            List<Map<String, Object>> previews = named.queryForList("SELECT * FROM (" + ITEMS + ") preview WHERE rn <= 3 ORDER BY created_at DESC, id", Map.of("ids", ids));
            for (Map<String, Object> customer : customers) {
                customer.put("items", previews.stream().filter(row -> row.get("user_id").equals(customer.get("user_id"))).map(this::serializeItem).toList());
                serializeDates(customer);
            }
        }
        return Map.of("content", customers, "totalElements", total == null ? 0 : total,
                "totalPages", total == null ? 0 : (total + size - 1) / size, "page", page);
    }

    public Map<String, Object> detail(UUID userId) {
        List<Map<String, Object>> customers = jdbc.queryForList(CUSTOMER_SELECT + CUSTOMER_FROM + " AND u.id = ?", userId);
        if (customers.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found");
        Map<String, Object> customer = customers.get(0);
        customer.put("items", named.queryForList(ITEMS + " ORDER BY ci.created_at DESC, ci.id", Map.of("ids", List.of(userId)))
                .stream().map(this::serializeItem).toList());
        customer.put("messages", jdbc.queryForList("SELECT id, subject, body, email_status, notification_status, created_at FROM admin_cart_messages WHERE user_id = ? ORDER BY created_at DESC, id LIMIT 20", userId)
                .stream().map(AdminCartActivityService::serializeDates).toList());
        return serializeDates(customer);
    }

    private Map<String, Object> serializeItem(Map<String, Object> row) {
        Object url = row.get("image_url");
        if (url != null) row.put("image_url", objects.getPresignedUrl(url.toString()));
        return serializeDates(row);
    }
    private static Map<String, Object> serializeDates(Map<String, Object> row) {
        row.replaceAll((key, value) -> value instanceof Timestamp t ? t.toInstant().toString() : value);
        return row;
    }

    public Map<String, Object> send(UUID userId, UUID adminId, UUID requestId, String subject, String body) {
        // Same request ID is safe to retry after a lost response; never resends an uncertain result.
        List<Map<String, Object>> previous = jdbc.queryForList("SELECT * FROM admin_cart_messages WHERE id = ?", requestId);
        if (!previous.isEmpty()) return existing(previous.get(0), userId, adminId, subject, body);
        List<Map<String, Object>> recipients = jdbc.queryForList(MarketingAudience.ELIGIBLE
                + " AND u.id = ? AND COALESCE(c.is_active, TRUE) = TRUE ORDER BY (c.provider = 'EMAIL') DESC, c.id LIMIT 1", userId);
        if (recipients.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Customer has no eligible email address, is inactive, or has opted out.");
        String recipient = recipients.get(0).get("email").toString();
        guard.checkCampaign(adminId, 1);
        int claimed = jdbc.update("INSERT INTO admin_cart_messages(id, user_id, admin_id, email, subject, body) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING",
                requestId, userId, adminId, recipient, subject, body);
        if (claimed == 0) return existing(jdbc.queryForMap("SELECT * FROM admin_cart_messages WHERE id = ?", requestId), userId, adminId, subject, body);
        Map<String, Object> customer = jdbc.queryForMap("SELECT first_name, email_opt_out_token FROM users WHERE id = ?", userId);
        UUID token = UUID.randomUUID();
        if (customer.get("email_opt_out_token") == null) {
            jdbc.update("UPDATE users SET email_opt_out_token = ? WHERE id = ? AND email_opt_out_token IS NULL", token, userId);
            customer = jdbc.queryForMap("SELECT first_name, email_opt_out_token FROM users WHERE id = ?", userId);
        }
        boolean accepted = email.sendCartActivityEmail(recipient, Objects.toString(customer.get("first_name"), ""), subject, body,
                baseUrl + "/api/email/opt-out?token=" + customer.get("email_opt_out_token"));
        jdbc.update("UPDATE admin_cart_messages SET email_status = ? WHERE id = ?", accepted ? "ACCEPTED" : "FAILED", requestId);
        jdbc.update("INSERT INTO notification_history(id, user_id, title, body, type, is_read, created_at) VALUES (?, ?, ?, ?, 'CART_MESSAGE', FALSE, NOW()) ON CONFLICT (id) DO NOTHING", requestId, userId, subject, body);
        jdbc.update("UPDATE admin_cart_messages SET notification_status = 'RECORDED' WHERE id = ?", requestId);
        try {
            push.deliverRecordedToUser(userId, subject, body.length() > 240 ? body.substring(0, 240) + "…" : body, Map.of("type", "CART_MESSAGE", "route", "/cart", "messageId", requestId.toString()));
            jdbc.update("UPDATE admin_cart_messages SET notification_status = 'QUEUED' WHERE id = ?", requestId);
        } catch (RuntimeException e) {
            jdbc.update("UPDATE admin_cart_messages SET notification_status = 'RECORDED' WHERE id = ?", requestId);
        }
        return serializeDates(jdbc.queryForMap("SELECT * FROM admin_cart_messages WHERE id = ?", requestId));
    }

    private Map<String, Object> existing(Map<String, Object> row, UUID userId, UUID adminId, String subject, String body) {
        if (!userId.equals(row.get("user_id")) || !adminId.equals(row.get("admin_id"))
                || !subject.equals(row.get("subject")) || !body.equals(row.get("body")))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Request ID already belongs to another message");
        return serializeDates(row);
    }
}
