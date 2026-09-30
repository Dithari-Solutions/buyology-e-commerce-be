package com.buyology.ecommerce.store.service;

import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.store.domain.Store;
import com.buyology.ecommerce.store.domain.StoreProduct;
import com.buyology.ecommerce.store.dto.FlashSaleRequest;
import com.buyology.ecommerce.store.repository.StoreProductRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Pins what the flash-sale batch may and may not write.
 *
 * <p>Every assertion here is a price a customer would have been charged. The batch endpoint applies
 * one campaign to many listings in one transaction, so a rule that only nearly holds does not produce
 * one bad row — it produces forty, live, at prices nobody typed.
 */
class FlashSaleServiceTest {

    private static final UUID STORE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID LISTING = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final StoreProductRepository storeProductRepository = mock(StoreProductRepository.class);
    private final StoreProductService storeProductService = mock(StoreProductService.class);
    private final FlashSaleService service =
            new FlashSaleService(storeProductRepository, storeProductService);

    private StoreProduct listing() {
        Store store = new Store();
        store.setId(STORE);
        Product product = new Product();
        product.setId(UUID.fromString("33333333-3333-3333-3333-333333333333"));
        product.setSku("MBP-14-M4");
        StoreProduct sp = new StoreProduct(store, product, new BigDecimal("2000.00"));
        sp.setId(LISTING);
        return sp;
    }

    /** A batch of one, 20% off, ending on a date well in the future. */
    private FlashSaleRequest campaign(FlashSaleRequest.FlashSaleItemRequest item) {
        FlashSaleRequest request = new FlashSaleRequest();
        request.setDiscountType(Product.DiscountType.PERCENTAGE);
        request.setDiscountValue(new BigDecimal("20"));
        request.setEndsOn(LocalDate.now().plusDays(7));
        request.setItems(List.of(item));
        return request;
    }

    private FlashSaleRequest.FlashSaleItemRequest item() {
        FlashSaleRequest.FlashSaleItemRequest item = new FlashSaleRequest.FlashSaleItemRequest();
        item.setStoreProductId(LISTING);
        return item;
    }

    private void listingExists(StoreProduct sp) {
        when(storeProductRepository.findById(LISTING)).thenReturn(Optional.of(sp));
        when(storeProductRepository.save(any(StoreProduct.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(storeProductService.toResponseBatch(anyList(), any(Instant.class))).thenReturn(List.of());
    }

    // ── The type and the value are one decision ──────────────────────────────

    @Test
    void anItemOverridingOnlyTheTypeIsRefusedInsteadOfInheritingTheBatchValue() {
        // The defect: resolved independently, this item became FIXED 20 — the batch's percentage VALUE
        // wearing the item's FIXED type — and validateDiscount waved it through because 20 really is
        // below 2000. A 2,000 AED laptop would have gone on sale at 20 AED.
        StoreProduct sp = listing();
        listingExists(sp);

        FlashSaleRequest.FlashSaleItemRequest item = item();
        item.setDiscountType(Product.DiscountType.FIXED);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.putOnFlashSale(campaign(item)));
        assertTrue(ex.getMessage().contains("discountValue is required"), ex.getMessage());
        assertNull(sp.getDiscountType(), "nothing may be written when the batch is refused");
        verify(storeProductRepository, never()).save(any(StoreProduct.class));
    }

    @Test
    void anItemOverridingOnlyTheValueIsRefusedTheSameWay() {
        // The mirror: 3499 against the batch's PERCENTAGE would be a 3,499% discount.
        StoreProduct sp = listing();
        listingExists(sp);

        FlashSaleRequest.FlashSaleItemRequest item = item();
        item.setDiscountValue(new BigDecimal("3499.00"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.putOnFlashSale(campaign(item)));
        assertTrue(ex.getMessage().contains("discountType is required"), ex.getMessage());
        verify(storeProductRepository, never()).save(any(StoreProduct.class));
    }

    @Test
    void anItemOverridingBothIsHonouredExactlyAsSent() {
        // The feature the override exists for: "everything 20% off, but the laptop at a fixed 1,499"
        // in one call. The item's pair must win outright, not blend with the batch's.
        StoreProduct sp = listing();
        listingExists(sp);

        FlashSaleRequest.FlashSaleItemRequest item = item();
        item.setDiscountType(Product.DiscountType.FIXED);
        item.setDiscountValue(new BigDecimal("1499.00"));

        service.putOnFlashSale(campaign(item));

        assertEquals(Product.DiscountType.FIXED, sp.getDiscountType());
        assertEquals(new BigDecimal("1499.00"), sp.getDiscountValue());
        assertNotNull(sp.getDiscountEndsAt(), "a flash sale must carry its end");
    }

    @Test
    void anItemWithNoOverrideTakesTheWholeBatchPair() {
        StoreProduct sp = listing();
        listingExists(sp);

        service.putOnFlashSale(campaign(item()));

        assertEquals(Product.DiscountType.PERCENTAGE, sp.getDiscountType());
        assertEquals(new BigDecimal("20"), sp.getDiscountValue());
    }

    // ── Variant-priced listings ──────────────────────────────────────────────

    @Test
    void aListingPricedPerVariantCanNowGoOnSaleAndTheSaleIsWritten() {
        // This used to be a hard refusal, and it was the largest thing standing between an admin and a
        // working flash sale: a variant-bearing listing could not be discounted at all, because its
        // variant lines were charged the raw variant price while the card advertised the parent's
        // discounted one. Every line is now priced from the listing row this sale writes to, so there is
        // nothing dishonest left to refuse.
        //
        // Note what this implies commercially, because the deploy is what triggers it: every
        // variant-bearing listing that already carries a discount starts selling at the discounted
        // price, including for baskets already sitting there.
        StoreProduct sp = listing();
        listingExists(sp);

        assertDoesNotThrow(() -> service.putOnFlashSale(campaign(item())));

        assertEquals(Product.DiscountType.PERCENTAGE, sp.getDiscountType());
        assertEquals(new BigDecimal("20"), sp.getDiscountValue());
        verify(storeProductRepository, times(1)).save(sp);
    }

    @Test
    void theBatchNoLongerCountsVariantsAtAllBecauseNothingDependsOnTheCount() {
        // The count existed only to feed validateNoActiveVariants. With the refusal gone the query is
        // gone too — a forty-product campaign should not pay for a lookup nothing reads.
        StoreProduct sp = listing();
        listingExists(sp);
        FlashSaleRequest request = campaign(item());
        request.setItems(List.of(item(), item(), item()));

        assertDoesNotThrow(() -> service.putOnFlashSale(request));

        assertEquals(2, FlashSaleService.class.getDeclaredConstructors()[0].getParameterCount(),
                "the variant repository was a collaborator only for the deleted refusal");
    }

    // ── The listing is bounded ───────────────────────────────────────────────

    @Test
    void theAdminListingIsPagedAndCappedEvenWhenTheCallerAsksForEverything() {
        // It used to select every discounted row in the shop and then build a response per row, each
        // fetching its own title and variants. A size nobody sanity-checks must not restore that.
        when(storeProductRepository.findFlashSaleAssignments(any(Instant.class), any(Pageable.class)))
                .thenReturn(List.of());
        when(storeProductService.toResponseBatch(anyList(), any(Instant.class))).thenReturn(List.of());

        service.getFlashSale(null, 0, 100_000);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(storeProductRepository).findFlashSaleAssignments(any(Instant.class), pageable.capture());
        assertEquals(200, pageable.getValue().getPageSize(), "the cap has to bind, not the request");
    }

    @Test
    void aNegativePageDoesNotBecomeANegativeOffset() {
        when(storeProductRepository.findFlashSaleAssignmentsByStore(
                any(UUID.class), any(Instant.class), any(Pageable.class))).thenReturn(List.of());
        when(storeProductService.toResponseBatch(anyList(), any(Instant.class))).thenReturn(List.of());

        service.getFlashSale(STORE, -5, 10);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(storeProductRepository).findFlashSaleAssignmentsByStore(
                any(UUID.class), any(Instant.class), pageable.capture());
        assertEquals(0, pageable.getValue().getPageNumber());
    }
}
