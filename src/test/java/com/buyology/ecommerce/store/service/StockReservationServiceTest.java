package com.buyology.ecommerce.store.service;

import com.buyology.ecommerce.order.domain.Order;
import com.buyology.ecommerce.order.domain.OrderItem;
import com.buyology.ecommerce.order.repository.OrderItemRepository;
import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.product.repository.ProductRepository;
import com.buyology.ecommerce.store.repository.StoreProductVariantRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pins the arithmetic of putting stock back.
 *
 * <p>Every assertion here is a unit of real inventory. Returning too little leaves the shop
 * refusing sales for goods on the shelf — the leak this service exists to close. Returning too
 * much is worse: it invents stock that does not exist, and the shop sells something it cannot
 * ship. So most of these tests are about the second kind.
 */
class StockReservationServiceTest {

    private static final UUID ORDER = UUID.fromString("3f2a1b4c-5d6e-4f70-8a91-b2c3d4e5f607");
    private static final UUID STORE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PRODUCT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID VARIANT = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private final OrderItemRepository itemRepo = mock(OrderItemRepository.class);
    private final StoreProductVariantRepository variantRepo = mock(StoreProductVariantRepository.class);
    private final ProductRepository productRepo = mock(ProductRepository.class);
    private final StockReservationService service =
            new StockReservationService(itemRepo, variantRepo, productRepo);

    private static Order reservedOrder() {
        Order o = new Order();
        o.setId(ORDER);
        o.setStockReservedAt(Instant.now());
        return o;
    }

    private static OrderItem item(UUID variantId, UUID storeId, int qty) {
        OrderItem i = new OrderItem();
        i.setProductId(PRODUCT);
        i.setVariantId(variantId);
        i.setStoreId(storeId);
        i.setQuantity(qty);
        return i;
    }

    private void itemsAre(OrderItem... items) {
        when(itemRepo.findAllByOrderId(ORDER)).thenReturn(List.of(items));
    }

    // ── The leak this closes ─────────────────────────────────────────────────

    @Test
    void putsTheUnitsBackOnTheShelf() {
        Order order = reservedOrder();
        itemsAre(item(VARIANT, STORE, 3));
        when(variantRepo.incrementStock(STORE, PRODUCT, VARIANT, 3)).thenReturn(1);

        assertTrue(service.releaseForOrder(order));

        verify(variantRepo).incrementStock(STORE, PRODUCT, VARIANT, 3);
        assertNotNull(order.getStockRestoredAt(), "the return has to be recorded, or it repeats");
    }

    @Test
    void alsoRestoresTheProductsDisplayStock() {
        // The storefront's "almost sold out" urgency reads this. Left alone, a shop that never
        // sold anything slowly reads as sold out.
        Order order = reservedOrder();
        Product p = new Product();
        p.setStockQuantity(7);
        itemsAre(item(VARIANT, STORE, 2));
        when(variantRepo.incrementStock(any(), any(), any(), anyInt())).thenReturn(1);
        when(productRepo.findById(PRODUCT)).thenReturn(Optional.of(p));

        service.releaseForOrder(order);

        assertEquals(9, p.getStockQuantity());
    }

    // ── Inventing stock: the failure worse than the leak ─────────────────────

    @Test
    void neverReturnsTwiceForTheSameOrder() {
        Order order = reservedOrder();
        order.setStockRestoredAt(Instant.now());

        assertFalse(service.releaseForOrder(order));

        verifyNoInteractions(variantRepo);
        verifyNoInteractions(itemRepo);
    }

    @Test
    void returnsNothingForAnOrderThatNeverReservedAnything() {
        // A B2B quote order: built without going through createOrder, so it never decremented.
        // Crediting it units would create inventory out of nothing.
        Order order = new Order();
        order.setId(ORDER);
        order.setStockReservedAt(null);

        assertFalse(service.releaseForOrder(order));

        verifyNoInteractions(variantRepo);
    }

    @Test
    void ignoresLinesThatNeverHadStock() {
        // Only variant lines with a store were ever decremented — this mirrors the condition in
        // createOrder that took the stock.
        Order order = reservedOrder();
        itemsAre(item(null, STORE, 4), item(VARIANT, null, 2));

        service.releaseForOrder(order);

        verify(variantRepo, never()).incrementStock(any(), any(), any(), anyInt());
    }

    @Test
    void ignoresZeroAndNegativeQuantities() {
        Order order = reservedOrder();
        itemsAre(item(VARIANT, STORE, 0));

        service.releaseForOrder(order);

        verify(variantRepo, never()).incrementStock(any(), any(), any(), anyInt());
    }

    // ── Partial failure must not block a cancellation ────────────────────────

    @Test
    void aDelistedListingIsLoggedButDoesNotThrow() {
        // The customer has already been told the order is cancelled. One listing that no longer
        // exists cannot be allowed to undo that.
        Order order = reservedOrder();
        itemsAre(item(VARIANT, STORE, 1));
        when(variantRepo.incrementStock(any(), any(), any(), anyInt())).thenReturn(0);

        assertDoesNotThrow(() -> service.releaseForOrder(order));
        assertNotNull(order.getStockRestoredAt());
    }

    @Test
    void toleratesAProductThatNoLongerExists() {
        Order order = reservedOrder();
        itemsAre(item(VARIANT, STORE, 1));
        when(variantRepo.incrementStock(any(), any(), any(), anyInt())).thenReturn(1);
        when(productRepo.findById(PRODUCT)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.releaseForOrder(order));
    }

    @Test
    void handlesAnOrderWithNoItems() {
        Order order = reservedOrder();
        itemsAre();

        assertTrue(service.releaseForOrder(order));
        assertNotNull(order.getStockRestoredAt());
    }

    // ── Deadlock avoidance ───────────────────────────────────────────────────

    @Test
    void touchesVariantRowsInAStableOrder() {
        // Two cancellations sharing variant rows must take those locks in the same sequence, or
        // they deadlock. Cancellation is about to become a routine operation, so this matters.
        UUID a = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000000");
        UUID b = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000000");
        Order order = reservedOrder();
        itemsAre(item(b, STORE, 1), item(a, STORE, 1));
        when(variantRepo.incrementStock(any(), any(), any(), anyInt())).thenReturn(1);

        service.releaseForOrder(order);

        var inOrder = inOrder(variantRepo);
        inOrder.verify(variantRepo).incrementStock(STORE, PRODUCT, a, 1);
        inOrder.verify(variantRepo).incrementStock(STORE, PRODUCT, b, 1);
    }

    @Test
    void aNullOrderIsANoOp() {
        assertFalse(service.releaseForOrder(null));
    }
}
