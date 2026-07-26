package com.buyology.ecommerce.erpnext.service;

import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import com.buyology.ecommerce.erpnext.config.ErpNextProperties;
import com.buyology.ecommerce.erpnext.dto.ErpProduct;
import com.buyology.ecommerce.erpnext.service.ErpNextClient.ErpNextException;
import com.buyology.ecommerce.order.domain.Order;
import com.buyology.ecommerce.order.domain.OrderItem;
import com.buyology.ecommerce.order.event.OrderPaidEvent;
import com.buyology.ecommerce.order.repository.OrderItemRepository;
import com.buyology.ecommerce.order.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pushes a PAID order into ERPNext as a <b>Sales Order</b> plus a <b>Sales Invoice</b>.
 *
 * <p>Triggered by {@link OrderPaidEvent}, which OrderService publishes only after the order
 * row is committed as PAID. The listener is {@code @Async} and swallows every failure into
 * {@code orders.erp_sync_error} — a broken or unreachable ERPNext can never roll back a
 * payment, block an order, or surface an error to the customer.
 *
 * <p>Idempotent: an order that already carries an ERPNext Sales Invoice is skipped, so a
 * manual re-sync from the admin ERP page is safe to press repeatedly.
 *
 * <p>Both documents are created standalone and cross-referenced through {@code po_no}
 * (set to the Buyology order id) rather than ERPNext's Sales Order → Invoice linkage,
 * which additionally requires matching child-row ids. The invoice is submitted but left
 * unpaid in ERPNext — reconciling the Paymob settlement into a Payment Entry is a separate
 * step and deliberately out of scope here.
 */
@Service
public class ErpOrderSyncService {

    private static final Logger log = LoggerFactory.getLogger(ErpOrderSyncService.class);
    private static final DateTimeFormatter ERP_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ErpNextProperties props;
    private final ErpNextClient client;
    private final OrderRepository orderRepo;
    private final OrderItemRepository orderItemRepo;
    private final AuthCredentialRepository authCredentialRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate txTemplate;

    public ErpOrderSyncService(ErpNextProperties props,
                               ErpNextClient client,
                               OrderRepository orderRepo,
                               OrderItemRepository orderItemRepo,
                               AuthCredentialRepository authCredentialRepository,
                               ObjectMapper objectMapper,
                               PlatformTransactionManager transactionManager) {
        this.props = props;
        this.client = client;
        this.orderRepo = orderRepo;
        this.orderItemRepo = orderItemRepo;
        this.authCredentialRepository = authCredentialRepository;
        this.objectMapper = objectMapper;
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    // ── entry points ──────────────────────────────────────────────────────────

    /** Fire-and-forget push once the order is committed as PAID. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPaid(OrderPaidEvent event) {
        if (!enabled()) return;
        try {
            syncOrder(event.getOrderId());
        } catch (Exception e) {
            // syncOrder already recorded the failure; this is the last-resort guard.
            log.error("[ERPNEXT] order sync threw for order {}: {}", event.getOrderId(), e.getMessage());
        }
    }

    /** True when both the module and the order push are switched on. */
    public boolean enabled() {
        return props.isEnabled() && props.isSyncOrders();
    }

    /**
     * Push one order to ERPNext. Safe to call repeatedly — an order that already has a
     * Sales Invoice is left untouched. Records success/failure on the order either way.
     *
     * @return a short human-readable outcome for the admin UI
     */
    public String syncOrder(UUID orderId) {
        try {
            // Loading can itself fail (missing SKU, no items) — keep it inside the try so the
            // reason is recorded on the order instead of escaping as an unhandled 500.
            Snapshot snap = txTemplate.execute(status -> loadSnapshot(orderId));
            if (snap == null) {
                return "Order not found";
            }
            if (snap.erpSalesInvoice != null && !snap.erpSalesInvoice.isBlank()) {
                return "Already synced (invoice " + snap.erpSalesInvoice + ")";
            }

            String[] docs = createErpDocuments(snap);
            String salesOrder = docs[0];
            String salesInvoice = docs[1];

            txTemplate.executeWithoutResult(status -> orderRepo.findById(orderId).ifPresent(o -> {
                o.setErpSalesOrder(salesOrder);
                o.setErpSalesInvoice(salesInvoice);
                o.setErpSyncedAt(Instant.now());
                o.setErpSyncError(null);
                orderRepo.save(o);
            }));

            log.info("[ERPNEXT] order {} synced — Sales Order {} / Sales Invoice {}",
                    orderId, salesOrder, salesInvoice);
            return "Synced: " + salesOrder + " / " + salesInvoice;

        } catch (Exception e) {
            String message = e.getMessage() == null ? e.toString() : e.getMessage();
            log.error("[ERPNEXT] order {} sync failed: {}", orderId, message);
            txTemplate.executeWithoutResult(status -> orderRepo.findById(orderId).ifPresent(o -> {
                o.setErpSyncError(trim(message, 1000));
                orderRepo.save(o);
            }));
            return "Failed: " + message;
        }
    }

    /**
     * Result of the mock-order test push. Mirrors what a real PAID order produces in ERPNext,
     * but is not persisted against any Buyology order.
     */
    public record MockResult(boolean ok, String salesOrder, String salesInvoice,
                             String salesOrderUrl, String salesInvoiceUrl,
                             String customer, List<String> itemCodes, String currency, String message) {}

    /**
     * Push a synthetic "mock" order to ERPNext through the <b>exact same code path</b> a real
     * PAID order uses (resolve/create Customer, resolve Items, create Sales Order + Sales
     * Invoice, honouring the submit/company/currency config). Nothing is written to the orders
     * table — this only proves the ERPNext write path end-to-end.
     *
     * <p>Line items reference <b>real</b> ERP item codes: the ones passed in, or (when none are
     * given) the most-recently-modified Item. Rate falls back to a token 100 when the item has
     * no standard_rate, so the test document has a non-zero total.
     */
    public MockResult createMockOrder(List<String> requestedItemCodes, String currencyArg) {
        if (!props.isEnabled()) {
            return new MockResult(false, null, null, null, null, null, null, null,
                    "ERPNext module is disabled. Set ERPNEXT_ENABLED=true (and base URL, key, secret) first.");
        }
        try {
            String currency = (currencyArg != null && !currencyArg.isBlank()) ? currencyArg.trim() : "AED";

            List<ErpProduct> items;
            if (requestedItemCodes != null && !requestedItemCodes.isEmpty()) {
                items = client.getItemsByCode(requestedItemCodes);
                if (items.isEmpty()) {
                    return new MockResult(false, null, null, null, null, null, null, currency,
                            "None of the given item codes exist in ERPNext");
                }
            } else {
                items = client.listProducts(1);
                if (items.isEmpty()) {
                    return new MockResult(false, null, null, null, null, null, null, currency,
                            "ERPNext has no Items to build a mock order from");
                }
            }

            Snapshot snap = new Snapshot();
            snap.orderId = UUID.randomUUID();
            snap.currency = currency;
            snap.discount = BigDecimal.ZERO;
            snap.shippingFee = BigDecimal.ZERO;
            snap.paidAt = Instant.now();
            snap.email = "erp-test@buyology.online";
            snap.customerName = "Buyology ERP Test";

            List<String> codes = new ArrayList<>();
            for (ErpProduct it : items) {
                String code = it.itemCode() != null && !it.itemCode().isBlank() ? it.itemCode() : it.name();
                if (code == null || code.isBlank()) continue;
                Line line = new Line();
                line.itemCode = code;
                line.itemName = it.itemName() != null && !it.itemName().isBlank() ? it.itemName() : code;
                line.qty = 1;
                line.rate = (it.standardRate() != null && it.standardRate() > 0)
                        ? BigDecimal.valueOf(it.standardRate())
                        : new BigDecimal("100.00");
                snap.lines.add(line);
                codes.add(code);
            }
            if (snap.lines.isEmpty()) {
                return new MockResult(false, null, null, null, null, null, null, currency,
                        "No usable items for the mock order");
            }

            String[] docs = createErpDocuments(snap);
            log.info("[ERPNEXT] mock order pushed — Sales Order {} / Sales Invoice {}", docs[0], docs[1]);
            return new MockResult(true, docs[0], docs[1],
                    client.deskUrl("Sales Order", docs[0]), client.deskUrl("Sales Invoice", docs[1]),
                    snap.customerName, codes, currency, "Mock order pushed to ERPNext");

        } catch (Exception e) {
            String message = e.getMessage() == null ? e.toString() : e.getMessage();
            log.error("[ERPNEXT] mock order failed: {}", message);
            return new MockResult(false, null, null, null, null, null, null, currencyArg, message);
        }
    }

    // ── ERPNext document builders ─────────────────────────────────────────────

    /**
     * Create the ERPNext Sales Order + Sales Invoice for a snapshot and return their names as
     * {@code [salesOrder, salesInvoice]}. Shared by the real order sync and the mock test push.
     */
    private String[] createErpDocuments(Snapshot snap) {
        String customer = ensureCustomer(snap);
        for (Line line : snap.lines) {
            ensureItem(line);
        }
        // Reuse an existing Sales Order if a previous attempt got that far but then failed.
        String salesOrder = snap.erpSalesOrder != null && !snap.erpSalesOrder.isBlank()
                ? snap.erpSalesOrder
                : createSalesOrder(snap, customer);
        String salesInvoice = createSalesInvoice(snap, customer, salesOrder);
        return new String[]{salesOrder, salesInvoice};
    }


    /**
     * Resolve (or create) the ERPNext Customer for this buyer. Looked up by email first —
     * the only genuinely unique handle we hold — then by display name.
     */
    private String ensureCustomer(Snapshot snap) {
        String email = snap.email;
        if (email != null && !email.isBlank()) {
            String byEmail = client.findDocumentName("Customer",
                    "[[\"email_id\",\"=\"," + ErpNextClient.quote(email) + "]]");
            if (byEmail != null) return byEmail;
        }

        String displayName = snap.customerName;
        String byName = client.findDocumentName("Customer",
                "[[\"customer_name\",\"=\"," + ErpNextClient.quote(displayName) + "]]");
        if (byName != null) return byName;

        if (!props.isAutoCreateCustomer()) {
            throw new ErpNextException("No ERPNext Customer matches \"" + displayName
                    + "\" and auto-create is disabled (erpnext.auto-create-customer).");
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("doctype", "Customer");
        body.put("customer_name", displayName);
        body.put("customer_type", "Individual");
        body.put("customer_group", props.getCustomerGroup());
        body.put("territory", props.getTerritory());
        if (email != null && !email.isBlank()) body.put("email_id", email);
        if (snap.phone != null && !snap.phone.isBlank()) body.put("mobile_no", snap.phone);

        try {
            return documentName(client.createDocument("Customer", body));
        } catch (ErpNextException e) {
            // A concurrent order for the same buyer may have created it a moment ago.
            String existing = client.findDocumentName("Customer",
                    "[[\"customer_name\",\"=\"," + ErpNextClient.quote(displayName) + "]]");
            if (existing != null) return existing;
            throw e;
        }
    }

    /**
     * Make sure the ordered SKU exists as an ERPNext Item — ERPNext rejects a Sales Order
     * referencing an unknown {@code item_code}. Auto-created items are <b>non-stock</b>
     * ({@code is_stock_item=0}) so selling them needs no warehouse balance, which keeps this
     * integration independent of ERPNext inventory.
     */
    private void ensureItem(Line line) {
        if (client.documentExists("Item", line.itemCode)) return;

        if (!props.isAutoCreateItems()) {
            throw new ErpNextException("ERPNext has no Item \"" + line.itemCode
                    + "\" and auto-create is disabled (erpnext.auto-create-items).");
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("doctype", "Item");
        body.put("item_code", line.itemCode);
        body.put("item_name", trim(line.itemName, 140));
        body.put("item_group", props.getDefaultItemGroup());
        body.put("stock_uom", props.getDefaultUom());
        body.put("is_stock_item", 0);
        body.put("include_item_in_manufacturing", 0);
        try {
            client.createDocument("Item", body);
        } catch (ErpNextException e) {
            if (client.documentExists("Item", line.itemCode)) return; // created concurrently
            throw e;
        }
    }

    private String createSalesOrder(Snapshot snap, String customer) {
        String date = ERP_DATE.format(snap.paidAt.atOffset(ZoneOffset.UTC).toLocalDate());
        String deliveryDate = ERP_DATE.format(
                snap.paidAt.atOffset(ZoneOffset.UTC).toLocalDate().plusDays(3));

        ObjectNode body = objectMapper.createObjectNode();
        body.put("doctype", "Sales Order");
        body.put("customer", customer);
        body.put("transaction_date", date);
        body.put("delivery_date", deliveryDate);
        body.put("po_no", snap.orderId.toString());
        applyCommon(body, snap, deliveryDate);
        return documentName(client.createDocument("Sales Order", body));
    }

    private String createSalesInvoice(Snapshot snap, String customer, String salesOrder) {
        String date = ERP_DATE.format(snap.paidAt.atOffset(ZoneOffset.UTC).toLocalDate());

        ObjectNode body = objectMapper.createObjectNode();
        body.put("doctype", "Sales Invoice");
        body.put("customer", customer);
        body.put("posting_date", date);
        body.put("due_date", date); // already paid at checkout
        body.put("po_no", snap.orderId.toString());
        body.put("remarks", "Buyology order " + snap.orderId
                + (salesOrder == null ? "" : " (Sales Order " + salesOrder + ")"));
        applyCommon(body, snap, null);
        return documentName(client.createDocument("Sales Invoice", body));
    }

    /** Fields shared by the Sales Order and the Sales Invoice. */
    private void applyCommon(ObjectNode body, Snapshot snap, String deliveryDate) {
        if (props.getCompany() != null && !props.getCompany().isBlank()) {
            body.put("company", props.getCompany());
        }
        if (snap.currency != null && !snap.currency.isBlank()) {
            body.put("currency", snap.currency);
        }

        ArrayNode items = body.putArray("items");
        for (Line line : snap.lines) {
            ObjectNode row = items.addObject();
            row.put("item_code", line.itemCode);
            row.put("item_name", trim(line.itemName, 140));
            row.put("qty", line.qty);
            row.put("rate", line.rate);
            if (deliveryDate != null) row.put("delivery_date", deliveryDate);
        }

        // Order-level discount maps to a Grand Total discount; per-line rates stay untouched.
        if (snap.discount != null && snap.discount.compareTo(BigDecimal.ZERO) > 0) {
            body.put("apply_discount_on", "Grand Total");
            body.put("discount_amount", snap.discount);
        }

        // Shipping needs a real ERPNext account head, which is company-specific — only
        // added when configured, otherwise ERPNext totals cover goods only.
        String shippingAccount = props.getShippingAccountHead();
        if (snap.shippingFee != null && snap.shippingFee.compareTo(BigDecimal.ZERO) > 0
                && shippingAccount != null && !shippingAccount.isBlank()) {
            ArrayNode taxes = body.putArray("taxes");
            ObjectNode row = taxes.addObject();
            row.put("charge_type", "Actual");
            row.put("account_head", shippingAccount);
            row.put("description", "Shipping");
            row.put("tax_amount", snap.shippingFee);
        }

        if (props.isSubmitDocuments()) {
            body.put("docstatus", 1);
        }
    }

    // ── snapshot loading ──────────────────────────────────────────────────────

    /**
     * Read everything the push needs inside one short transaction, so the HTTP calls to
     * ERPNext never hold a database connection open.
     */
    private Snapshot loadSnapshot(UUID orderId) {
        Order order = orderRepo.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("[ERPNEXT] order {} not found — nothing to sync", orderId);
            return null;
        }

        Snapshot snap = new Snapshot();
        snap.orderId = order.getId();
        snap.currency = order.getCurrency();
        snap.discount = order.getDiscount();
        snap.shippingFee = order.getShippingFee();
        snap.paidAt = order.getPaidAt() != null ? order.getPaidAt() : Instant.now();
        snap.erpSalesOrder = order.getErpSalesOrder();
        snap.erpSalesInvoice = order.getErpSalesInvoice();
        snap.phone = order.getRecipientPhone();

        snap.email = order.getUserId() == null ? null
                : authCredentialRepository.findByUserId(order.getUserId()).stream()
                    .map(c -> c.getEmail())
                    .filter(e -> e != null && !e.isBlank())
                    .findFirst().orElse(null);

        snap.customerName = buildCustomerName(order, snap.email);

        for (OrderItem item : orderItemRepo.findAllByOrderId(orderId)) {
            String sku = item.getVariantSku() != null && !item.getVariantSku().isBlank()
                    ? item.getVariantSku()
                    : item.getProductSku();
            if (sku == null || sku.isBlank()) {
                throw new ErpNextException("Order item " + item.getId()
                        + " has no SKU — cannot map it to an ERPNext item_code.");
            }
            Line line = new Line();
            line.itemCode = sku;
            line.itemName = sku;
            line.qty = item.getQuantity() == null ? 0 : item.getQuantity();
            line.rate = item.getUnitPrice() == null ? BigDecimal.ZERO : item.getUnitPrice();
            snap.lines.add(line);
        }

        if (snap.lines.isEmpty()) {
            throw new ErpNextException("Order has no items — nothing to push to ERPNext.");
        }
        return snap;
    }

    /**
     * Display name for the ERPNext Customer. The account record is often incomplete, so fall
     * back through the order's recipient snapshot, then the email, then the order id.
     */
    private String buildCustomerName(Order order, String email) {
        String first = order.getRecipientFirstName();
        String last = order.getRecipientLastName();
        String full = ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
        if (!full.isBlank()) return trim(full, 140);
        if (email != null && !email.isBlank()) return trim(email, 140);
        return "Buyology Customer " + order.getId();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Pull the {@code name} (primary key) out of a created-document response. */
    private String documentName(com.fasterxml.jackson.databind.JsonNode created) {
        com.fasterxml.jackson.databind.JsonNode name = created == null ? null : created.get("name");
        if (name == null || name.isNull()) {
            throw new ErpNextException("ERPNext response did not contain a document name");
        }
        return name.asText();
    }

    private static String trim(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }

    // ── plain carriers (no JPA entities cross the transaction boundary) ────────

    private static class Snapshot {
        UUID orderId;
        String currency;
        BigDecimal discount;
        BigDecimal shippingFee;
        Instant paidAt;
        String email;
        String phone;
        String customerName;
        String erpSalesOrder;
        String erpSalesInvoice;
        final List<Line> lines = new ArrayList<>();
    }

    private static class Line {
        String itemCode;
        String itemName;
        int qty;
        BigDecimal rate;
    }
}
