package com.buyology.ecommerce.warranty.service;

import com.buyology.ecommerce.order.domain.Order;
import com.buyology.ecommerce.order.domain.OrderItem;
import com.buyology.ecommerce.order.domain.enums.OrderStatus;
import com.buyology.ecommerce.order.repository.OrderRepository;
import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.product.repository.ProductRepository;
import com.buyology.ecommerce.warranty.domain.WarrantyCard;
import com.buyology.ecommerce.warranty.domain.WarrantyCardStatus;
import com.buyology.ecommerce.warranty.dto.WarrantySpecLine;
import com.buyology.ecommerce.warranty.repository.WarrantyCardRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class WarrantyCardServiceTest {

    private static final Clock OCT_24 = Clock.fixed(Instant.parse("2026-10-24T10:00:00Z"), ZoneOffset.UTC);

    private final WarrantyCardRepository cards = mock(WarrantyCardRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final WarrantyProductSnapshotter snapshotter = mock(WarrantyProductSnapshotter.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private WarrantyCardService service(String coverage) {
        return new WarrantyCardService(cards, orders, products, snapshotter, objectMapper, coverage, OCT_24);
    }

    private final WarrantyCardService service = service("");

    private final UUID laptopId = UUID.randomUUID();
    private final UUID cableId = UUID.randomUUID();

    private Product product(UUID id, Integer months) {
        Product p = new Product();
        p.setId(id);
        p.setWarrantyMonths(months);
        when(products.findById(id)).thenReturn(Optional.of(p));
        return p;
    }

    private OrderItem item(UUID productId, int quantity) {
        OrderItem i = new OrderItem();
        i.setId(UUID.randomUUID());
        i.setProductId(productId);
        i.setQuantity(quantity);
        i.setProductSku("SKU-" + quantity);
        return i;
    }

    private Order order(OrderStatus status, Instant deliveredAt, OrderItem... items) {
        Order o = new Order();
        o.setId(UUID.randomUUID());
        o.setStatus(status);
        o.setDeliveredAt(deliveredAt);
        o.setItems(new ArrayList<>(List.of(items)));
        when(orders.findById(o.getId())).thenReturn(Optional.of(o));
        return o;
    }

    private void laptopSnapshot() {
        when(snapshotter.snapshot(eq(laptopId), any())).thenReturn(new WarrantyProductSnapshotter.Snapshot(
                "Dell Latitude 7420", "Dell", "Laptop", "Refurbished · Grade A",
                List.of(new WarrantySpecLine("Memory", "16 GB"))));
    }

    private List<WarrantyCard> savedCards() {
        ArgumentCaptor<WarrantyCard> saved = ArgumentCaptor.forClass(WarrantyCard.class);
        verify(cards, atLeast(0)).save(saved.capture());
        return saved.getAllValues();
    }

    // ── Issuing ───────────────────────────────────────────────────────────────

    @Test
    void issuesOneCardPerUnitStartingOnTheDeliveryDate() {
        product(laptopId, 12);
        product(cableId, null); // no warranty stated: no card
        laptopSnapshot();
        OrderItem laptops = item(laptopId, 2);
        Order order = order(OrderStatus.DELIVERED, Instant.parse("2026-06-24T21:30:00Z"), laptops, item(cableId, 1));

        int created = service.issueForOrder(order.getId());

        assertEquals(2, created);
        List<WarrantyCard> issued = savedCards();
        assertEquals(List.of(1, 2), issued.stream().map(WarrantyCard::getUnitIndex).toList());
        WarrantyCard first = issued.get(0);
        assertEquals(LocalDate.of(2026, 6, 24), first.getStartsOn(), "cover starts on the UTC delivery date");
        assertEquals(LocalDate.of(2027, 6, 24), first.getEndsOn());
        assertEquals(12, first.getDurationMonths());
        assertEquals(order.getId(), first.getOrderId());
        assertEquals(laptops.getId(), first.getOrderItemId());
        assertEquals(WarrantyCardStatus.ACTIVE, first.getStatus());
        assertEquals("Dell Latitude 7420", first.getProductName());
        assertEquals("Refurbished · Grade A", first.getConditionLabel());
        assertTrue(first.getSpecs().contains("\"Memory\""));
        assertNotEquals(issued.get(0).getSerialNumber(), issued.get(1).getSerialNumber());
        assertTrue(first.getSerialNumber().matches("DTH-[0-9A-Z]{4}-[0-9A-Z]{4}-[0-9A-Z]{4}"));
        verify(snapshotter, times(1)).snapshot(eq(laptopId), any()); // once per line, not per unit
    }

    @Test
    void runningAgainOnlyFillsInMissingUnits() {
        product(laptopId, 12);
        laptopSnapshot();
        OrderItem laptops = item(laptopId, 2);
        Order order = order(OrderStatus.DELIVERED, Instant.parse("2026-06-24T10:00:00Z"), laptops);
        WarrantyCard existing = new WarrantyCard();
        existing.setOrderItemId(laptops.getId());
        existing.setUnitIndex(1);
        when(cards.findByOrderIdOrderByUnitIndexAsc(order.getId())).thenReturn(List.of(existing));

        assertEquals(1, service.issueForOrder(order.getId()));
        assertEquals(List.of(2), savedCards().stream().map(WarrantyCard::getUnitIndex).toList());
    }

    @Test
    void aFullyIssuedOrderIsLeftAlone() {
        product(laptopId, 12);
        OrderItem laptops = item(laptopId, 1);
        Order order = order(OrderStatus.DELIVERED, Instant.parse("2026-06-24T10:00:00Z"), laptops);
        WarrantyCard existing = new WarrantyCard();
        existing.setOrderItemId(laptops.getId());
        existing.setUnitIndex(1);
        when(cards.findByOrderIdOrderByUnitIndexAsc(order.getId())).thenReturn(List.of(existing));

        assertEquals(0, service.issueForOrder(order.getId()));
        verify(cards, never()).save(any());
        verifyNoInteractions(snapshotter);
    }

    @Test
    void ordersThatWereNotDeliveredGetNoCards() {
        product(laptopId, 12);
        Order inTransit = order(OrderStatus.IN_TRANSIT, null, item(laptopId, 1));
        Order cancelled = order(OrderStatus.CANCELLED, null, item(laptopId, 1));

        assertEquals(0, service.issueForOrder(inTransit.getId()));
        assertEquals(0, service.issueForOrder(cancelled.getId()));
        assertEquals(0, service.issueForOrder(UUID.randomUUID()), "an unknown order is not an error");
        verify(cards, never()).save(any());
    }

    @Test
    void aNamelessProductStillGetsACardUnderItsSku() {
        product(laptopId, 6);
        when(snapshotter.snapshot(eq(laptopId), any())).thenReturn(
                new WarrantyProductSnapshotter.Snapshot(null, null, null, "New", List.of()));
        Order order = order(OrderStatus.DELIVERED, Instant.parse("2026-06-24T10:00:00Z"), item(laptopId, 1));

        service.issueForOrder(order.getId());

        assertEquals("SKU-1", savedCards().get(0).getProductName());
    }

    // ── Public lookup ─────────────────────────────────────────────────────────

    private WarrantyCard storedCard(String serial, LocalDate startsOn, int months) {
        WarrantyCard card = new WarrantyCard();
        card.setSerialNumber(serial);
        card.setStartsOn(startsOn);
        card.setEndsOn(startsOn.plusMonths(months));
        card.setDurationMonths(months);
        card.setStatus(WarrantyCardStatus.ACTIVE);
        card.setProductName("Dell Latitude 7420");
        card.setBrandName("Dell");
        card.setCategoryName("Laptop");
        card.setConditionLabel("Refurbished · Grade A");
        card.setSpecs("[{\"label\":\"Memory\",\"value\":\"16 GB\"}]");
        when(cards.findBySerialNumber(serial)).thenReturn(Optional.of(card));
        return card;
    }

    @Test
    void lookupFindsTheCardFromWhatAPersonTyped() {
        storedCard("DTH-7K4Q-2M9X-QH3T", LocalDate.of(2026, 6, 24), 12);

        var card = service.lookup(" dth 7k4q 2m9x qh3t ");

        assertEquals("DTH-7K4Q-2M9X-QH3T", card.serialNumber());
        assertEquals("ACTIVE", card.status());
        assertEquals("2026-06-24", card.startDate());
        assertEquals("2027-06-24", card.endDate());
        assertEquals(12, card.durationMonths());
        assertEquals("Buyology", card.soldBy());
        assertNull(card.coverage(), "no coverage text configured, so the section is hidden");
        assertEquals("Dell Latitude 7420", card.product().name());
        assertEquals(List.of(new WarrantySpecLine("Memory", "16 GB")), card.product().specs());
    }

    @Test
    void statusIsComputedOnTheDayOfTheLookup() {
        storedCard("DTH-AAAA-BBBB-CCCC", LocalDate.of(2025, 11, 1), 12); // ends 1 Nov 2026
        storedCard("DTH-DDDD-EEEE-FFFF", LocalDate.of(2025, 1, 1), 6);   // ended 1 Jul 2025

        assertEquals("EXPIRING_SOON", service.lookup("DTH-AAAA-BBBB-CCCC").status());
        assertEquals("EXPIRED", service.lookup("DTH-DDDD-EEEE-FFFF").status());
    }

    @Test
    void configuredCoverageTextIsShownOnEveryCard() {
        storedCard("DTH-7K4Q-2M9X-QH3T", LocalDate.of(2026, 6, 24), 12);
        assertEquals("Hardware defects under normal use.",
                service("  Hardware defects under normal use. ").lookup("DTH-7K4Q-2M9X-QH3T").coverage());
    }

    @Test
    void anUnknownSerialIsNotFoundAndSaysNothingMore() {
        when(cards.findBySerialNumber(anyString())).thenReturn(Optional.empty());
        var e = assertThrows(NoSuchElementException.class, () -> service.lookup("DTH-0000-0000-0000"));
        assertEquals(WarrantyCardService.NOT_FOUND, e.getMessage());
        assertThrows(NoSuchElementException.class, () -> service.lookup("   "));
    }

    @Test
    void unreadableStoredSpecsShowNoSpecsRatherThanFailingTheLookup() {
        storedCard("DTH-7K4Q-2M9X-QH3T", LocalDate.of(2026, 6, 24), 12).setSpecs("{not json");
        assertEquals(List.of(), service.lookup("DTH-7K4Q-2M9X-QH3T").product().specs());
    }

    // ── Void and restore ──────────────────────────────────────────────────────

    @Test
    void voidingWithdrawsTheCardAndKeepsWhoAndWhyForStaff() {
        WarrantyCard card = storedCard("DTH-7K4Q-2M9X-QH3T", LocalDate.of(2026, 6, 24), 12);
        UUID admin = UUID.randomUUID();

        var view = service.voidCard("dth-7k4q-2m9x-qh3t", "  <b>Returned</b> and refunded ", admin);

        assertEquals("VOID", view.status());
        assertEquals(WarrantyCardStatus.VOID, card.getStatus());
        assertEquals("Returned and refunded", card.getVoidReason());
        assertEquals(admin, card.getVoidedBy());
        assertNotNull(card.getVoidedAt());
        assertEquals("VOID", service.lookup("DTH-7K4Q-2M9X-QH3T").status());
        assertThrows(IllegalStateException.class, () -> service.voidCard("DTH-7K4Q-2M9X-QH3T", null, admin));
    }

    @Test
    void restoringUndoesAVoidAndOnlyAVoid() {
        WarrantyCard card = storedCard("DTH-7K4Q-2M9X-QH3T", LocalDate.of(2026, 6, 24), 12);
        UUID admin = UUID.randomUUID();
        assertThrows(IllegalStateException.class, () -> service.restoreCard("DTH-7K4Q-2M9X-QH3T", admin));

        service.voidCard("DTH-7K4Q-2M9X-QH3T", "mistake", admin);
        var view = service.restoreCard("DTH-7K4Q-2M9X-QH3T", admin);

        assertEquals("ACTIVE", view.status());
        assertNull(card.getVoidReason());
        assertNull(card.getVoidedAt());
        assertNull(card.getVoidedBy());
    }
}
