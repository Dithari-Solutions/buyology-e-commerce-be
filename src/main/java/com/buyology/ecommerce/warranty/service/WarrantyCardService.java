package com.buyology.ecommerce.warranty.service;

import com.buyology.ecommerce.common.utils.HtmlSanitizer;
import com.buyology.ecommerce.order.domain.Order;
import com.buyology.ecommerce.order.domain.OrderItem;
import com.buyology.ecommerce.order.domain.enums.OrderStatus;
import com.buyology.ecommerce.order.repository.OrderRepository;
import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.product.repository.ProductRepository;
import com.buyology.ecommerce.warranty.domain.WarrantyCard;
import com.buyology.ecommerce.warranty.domain.WarrantyCardStatus;
import com.buyology.ecommerce.warranty.dto.OrderItemWarranty;
import com.buyology.ecommerce.warranty.dto.PublicWarrantyResponse;
import com.buyology.ecommerce.warranty.dto.WarrantySpecLine;
import com.buyology.ecommerce.warranty.repository.WarrantyCardRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Issues warranty cards when an order is delivered, and answers the public lookup.
 *
 * <p>Cover starts on the delivery date, in UTC: that is when the customer has the unit, and it is
 * also what the refund window is measured from. Cards are never issued for an order that was not
 * delivered, so a cancelled order never needs its cards cleaned up.
 */
@Service
public class WarrantyCardService {

    private static final Logger log = LoggerFactory.getLogger(WarrantyCardService.class);

    static final String NOT_FOUND = "No warranty card matches this serial number";
    static final String SOLD_BY = "Buyology";
    private static final int MAX_VOID_REASON = 255;
    private static final int SERIAL_ATTEMPTS = 5;
    private static final TypeReference<List<WarrantySpecLine>> SPEC_LINES = new TypeReference<>() { };

    private final WarrantyCardRepository cards;
    private final OrderRepository orders;
    private final ProductRepository products;
    private final WarrantyProductSnapshotter snapshotter;
    private final ObjectMapper objectMapper;
    private final String coverageText;
    private final Clock clock;

    @Autowired
    public WarrantyCardService(WarrantyCardRepository cards,
                               OrderRepository orders,
                               ProductRepository products,
                               WarrantyProductSnapshotter snapshotter,
                               ObjectMapper objectMapper,
                               @Value("${app.warranty.coverage-text:}") String coverageText) {
        this(cards, orders, products, snapshotter, objectMapper, coverageText, Clock.systemUTC());
    }

    WarrantyCardService(WarrantyCardRepository cards, OrderRepository orders, ProductRepository products,
                        WarrantyProductSnapshotter snapshotter, ObjectMapper objectMapper,
                        String coverageText, Clock clock) {
        this.cards = cards;
        this.orders = orders;
        this.products = products;
        this.snapshotter = snapshotter;
        this.objectMapper = objectMapper;
        this.coverageText = coverageText == null || coverageText.isBlank() ? null : coverageText.trim();
        this.clock = clock;
    }

    /**
     * Gives every unit on a delivered order whose product states a warranty a card of its own.
     *
     * <p>Idempotent: units that already have a card are skipped, and (order item, unit) is unique
     * in the table, so the delivery listener and the hourly sweep can both run for one order.
     *
     * @return how many cards were created
     */
    @Transactional
    public int issueForOrder(UUID orderId) {
        Order order = orders.findById(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.DELIVERED || order.getDeliveredAt() == null) {
            return 0;
        }
        LocalDate startsOn = LocalDate.ofInstant(order.getDeliveredAt(), ZoneOffset.UTC);

        Map<UUID, Set<Integer>> issuedUnits = new HashMap<>();
        for (WarrantyCard existing : cards.findByOrderIdOrderByUnitIndexAsc(orderId)) {
            issuedUnits.computeIfAbsent(existing.getOrderItemId(), k -> new HashSet<>()).add(existing.getUnitIndex());
        }

        int created = 0;
        for (OrderItem item : order.getItems()) {
            int quantity = item.getQuantity() == null ? 0 : item.getQuantity();
            if (item.getProductId() == null || quantity < 1) continue;
            Set<Integer> have = issuedUnits.getOrDefault(item.getId(), Set.of());
            if (have.size() >= quantity) continue;

            Integer months = products.findById(item.getProductId()).map(Product::getWarrantyMonths).orElse(null);
            if (months == null || months < 1) continue;

            // One snapshot per item, not per unit: every unit of a line is the same product.
            WarrantyProductSnapshotter.Snapshot snapshot = snapshotter.snapshot(item.getProductId(), item.getVariantId());
            String specsJson = writeSpecs(snapshot.specs());
            for (int unit = 1; unit <= quantity; unit++) {
                if (have.contains(unit)) continue;
                WarrantyCard card = new WarrantyCard();
                card.setSerialNumber(newSerial());
                card.setOrderId(orderId);
                card.setOrderItemId(item.getId());
                card.setUnitIndex(unit);
                card.setProductId(item.getProductId());
                card.setVariantId(item.getVariantId());
                card.setDurationMonths(months);
                card.setStartsOn(startsOn);
                // Last covered day: a 12-month card from 24 Jun 2026 covers through 24 Jun 2027.
                card.setEndsOn(startsOn.plusMonths(months));
                card.setStatus(WarrantyCardStatus.ACTIVE);
                card.setProductName(snapshot.name() != null && !snapshot.name().isBlank()
                        ? snapshot.name() : fallbackName(item));
                card.setBrandName(snapshot.brand());
                card.setCategoryName(snapshot.category());
                card.setConditionLabel(snapshot.condition());
                card.setSpecs(specsJson);
                cards.save(card);
                created++;
            }
        }
        if (created > 0) {
            log.info("[WARRANTY] Issued {} warranty card(s) for order {}", created, orderId);
        }
        return created;
    }

    /** The public view of one card. Throws NoSuchElementException (404) when nothing matches. */
    @Transactional(readOnly = true)
    public PublicWarrantyResponse lookup(String rawSerial) {
        WarrantyCard card = find(rawSerial);
        PublicWarrantyResponse.Product product = new PublicWarrantyResponse.Product(
                card.getProductName(),
                card.getBrandName(),
                card.getCategoryName(),
                card.getConditionLabel(),
                readSpecs(card));
        return new PublicWarrantyResponse(
                card.getSerialNumber(),
                WarrantyCardView.status(card, today()),
                card.getDurationMonths(),
                card.getStartsOn().toString(),
                card.getEndsOn().toString(),
                SOLD_BY,
                coverageText,
                product);
    }

    /** Withdraws a card, typically because the unit came back. The reason is kept for staff only. */
    @Transactional
    public OrderItemWarranty voidCard(String rawSerial, String reason, UUID adminUserId) {
        WarrantyCard card = find(rawSerial);
        if (card.getStatus() == WarrantyCardStatus.VOID) {
            throw new IllegalStateException("This warranty card is already void");
        }
        String cleaned = reason == null ? null : HtmlSanitizer.stripHtml(reason).trim();
        if (cleaned != null && cleaned.length() > MAX_VOID_REASON) cleaned = cleaned.substring(0, MAX_VOID_REASON);
        card.setStatus(WarrantyCardStatus.VOID);
        card.setVoidReason(cleaned == null || cleaned.isEmpty() ? null : cleaned);
        card.setVoidedAt(Instant.now(clock));
        card.setVoidedBy(adminUserId);
        log.info("[WARRANTY] Card {} voided by {}", card.getSerialNumber(), adminUserId);
        return WarrantyCardView.forOrderItem(card, today());
    }

    /** Undoes a void. The card's dates were never touched, so it reads exactly as before. */
    @Transactional
    public OrderItemWarranty restoreCard(String rawSerial, UUID adminUserId) {
        WarrantyCard card = find(rawSerial);
        if (card.getStatus() != WarrantyCardStatus.VOID) {
            throw new IllegalStateException("This warranty card is not void");
        }
        card.setStatus(WarrantyCardStatus.ACTIVE);
        card.setVoidReason(null);
        card.setVoidedAt(null);
        card.setVoidedBy(null);
        log.info("[WARRANTY] Card {} restored by {}", card.getSerialNumber(), adminUserId);
        return WarrantyCardView.forOrderItem(card, today());
    }

    private WarrantyCard find(String rawSerial) {
        String serial = WarrantySerials.canonicalize(rawSerial);
        Optional<WarrantyCard> card = serial == null ? Optional.empty() : cards.findBySerialNumber(serial);
        return card.orElseThrow(() -> new NoSuchElementException(NOT_FOUND));
    }

    private String newSerial() {
        // 60 random bits make a clash practically impossible; checking is cheap insurance, and the
        // unique constraint is the last word if two hosts ever drew the same one at once.
        for (int attempt = 0; attempt < SERIAL_ATTEMPTS; attempt++) {
            String serial = WarrantySerials.generate();
            if (!cards.existsBySerialNumber(serial)) return serial;
        }
        throw new IllegalStateException("Could not generate a unique warranty serial number");
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ZoneOffset.UTC));
    }

    private String writeSpecs(List<WarrantySpecLine> specs) {
        try {
            return objectMapper.writeValueAsString(specs == null ? List.of() : specs);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not store warranty card specs", e);
        }
    }

    // A card stays readable whatever is in this column: unreadable JSON shows no specs, not a 500.
    private List<WarrantySpecLine> readSpecs(WarrantyCard card) {
        if (card.getSpecs() == null || card.getSpecs().isBlank()) return List.of();
        try {
            return objectMapper.readValue(card.getSpecs(), SPEC_LINES);
        } catch (JsonProcessingException e) {
            log.warn("[WARRANTY] Unreadable specs on card {}: {}", card.getSerialNumber(), e.getMessage());
            return List.of();
        }
    }

    private static String fallbackName(OrderItem item) {
        String sku = item.getVariantSku() != null && !item.getVariantSku().isBlank()
                ? item.getVariantSku() : item.getProductSku();
        return sku != null && !sku.isBlank() ? sku : "Product";
    }
}
