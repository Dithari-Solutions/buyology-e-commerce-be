package com.buyology.ecommerce.store.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.product.domain.ProductVariant;
import com.buyology.ecommerce.product.repository.ProductRepository;
import com.buyology.ecommerce.product.repository.ProductTranslationRepository;
import com.buyology.ecommerce.product.repository.ProductVariantRepository;
import com.buyology.ecommerce.store.domain.Store;
import com.buyology.ecommerce.store.domain.StoreProduct;
import com.buyology.ecommerce.store.domain.StoreProductVariant;
import com.buyology.ecommerce.store.dto.UpdateStoreProductRequest;
import com.buyology.ecommerce.store.dto.UpdateStoreVariantRequest;
import com.buyology.ecommerce.store.repository.StoreProductRepository;
import com.buyology.ecommerce.store.repository.StoreProductVariantRepository;
import com.buyology.ecommerce.store.repository.StoreRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins that the accepted landmine of the pricing decision is at least VISIBLE the first time it
 * happens.
 *
 * <p>Two refusals used to make it unreachable — {@code validateNoActiveVariants} and
 * {@code requireVariantFreePricing} — and removing them was right: they were written for behaviour that
 * has stopped being true (a variant line is no longer priced from the variant row) and between them
 * they made a flash sale impossible on any product that has variants, which is much of the catalogue.
 *
 * <p>But nothing replaced them, and nothing anywhere compares a variant's {@code store_price} to its
 * parent listing's. Every cart line is priced from the PARENT, so a 5000 variant sitting under a 1000
 * listing sells at 1000 — silently, correctly by the rules, and for as long as it takes somebody to
 * notice it in a revenue report.
 *
 * <p>So the replacement is a signal rather than a guard: a WARN at the write path, both when the
 * variant is written and when the listing is. A refusal here would reinstate exactly what was removed.
 */
class VariantPriceDivergenceDetectionTest {

    private static final UUID STORE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID LISTING = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID STORE_VARIANT = UUID.fromString("55555555-5555-5555-5555-555555555555");

    private final StoreRepository storeRepository = mock(StoreRepository.class);
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final ProductVariantRepository variantRepository = mock(ProductVariantRepository.class);
    private final ProductTranslationRepository translationRepository = mock(ProductTranslationRepository.class);
    private final StoreProductRepository storeProductRepository = mock(StoreProductRepository.class);
    private final StoreProductVariantRepository storeProductVariantRepository =
            mock(StoreProductVariantRepository.class);

    private final StoreProductService service = new StoreProductService(
            storeRepository, productRepository, variantRepository, translationRepository,
            storeProductRepository, storeProductVariantRepository);

    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();
    private final ch.qos.logback.classic.Logger serviceLogger =
            (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(StoreProductService.class);

    private StoreProduct listing;
    private StoreProductVariant storeVariant;

    @BeforeEach
    void setUp() {
        logged.start();
        serviceLogger.addAppender(logged);

        Store store = new Store();
        store.setId(STORE);
        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setSku("MBP-14-M4");

        listing = new StoreProduct(store, product, new BigDecimal("1000.00"));
        listing.setId(LISTING);

        ProductVariant variant = new ProductVariant();
        variant.setId(UUID.randomUUID());
        variant.setProduct(product);
        variant.setSku("MBP-14-M4-1TB");

        storeVariant = new StoreProductVariant();
        storeVariant.setId(STORE_VARIANT);
        storeVariant.setStoreProduct(listing);
        storeVariant.setVariant(variant);
        storeVariant.setStorePrice(new BigDecimal("1000.00"));
        storeVariant.setStock(3);
        storeVariant.setIsActive(true);

        when(storeProductRepository.findById(LISTING)).thenReturn(Optional.of(listing));
        when(storeProductRepository.save(any(StoreProduct.class))).thenAnswer(inv -> inv.getArgument(0));
        when(storeProductVariantRepository.findById(STORE_VARIANT)).thenReturn(Optional.of(storeVariant));
        when(storeProductVariantRepository.save(any(StoreProductVariant.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(storeProductVariantRepository.findByStoreProduct_Id(LISTING)).thenReturn(List.of(storeVariant));
        when(translationRepository.findByProductId(any())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        serviceLogger.detachAppender(logged);
    }

    private List<String> warnings() {
        return logged.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    @Test
    void aVariantPricedAboveItsListingIsWarnedAboutAndNotRefused() {
        // The 5000-under-1000 case, which the shop will sell at 1000 without complaint.
        UpdateStoreVariantRequest request = new UpdateStoreVariantRequest();
        request.setStorePrice(new BigDecimal("5000.00"));

        assertDoesNotThrow(() -> service.updateStoreVariant(STORE, LISTING, STORE_VARIANT, request));

        assertTrue(warnings().stream().anyMatch(m -> m.contains("5000.00") && m.contains("1000.00")),
                "the write must succeed AND say so: " + warnings());
    }

    @Test
    void aVariantThatMirrorsItsListingIsSilent() {
        // The ordinary case, and it has to stay quiet or the signal is worthless.
        UpdateStoreVariantRequest request = new UpdateStoreVariantRequest();
        request.setStock(7);

        service.updateStoreVariant(STORE, LISTING, STORE_VARIANT, request);

        assertEquals(List.of(), warnings());
    }

    @Test
    void aSaleOnTheListingDoesNotTurnEveryMirroredVariantIntoAWarning() {
        // Compared against the LIST price, not the discounted one, and with no clock involved: a variant
        // that mirrors its listing must stay silent through every sale that listing goes on, or a
        // fortnight of flash sales buries the one row that actually diverges.
        listing.setDiscountType(Product.DiscountType.PERCENTAGE);
        listing.setDiscountValue(new BigDecimal("25"));

        service.updateStoreVariant(STORE, LISTING, STORE_VARIANT, new UpdateStoreVariantRequest());

        assertEquals(List.of(), warnings());
    }

    @Test
    void loweringTheLISTINGUnderAVariantThatKeepsItsOwnPriceIsWarnedAboutToo() {
        // The same state reached from the other side, and the edit an admin is far more likely to make.
        storeVariant.setStorePrice(new BigDecimal("5000.00"));

        UpdateStoreProductRequest request = new UpdateStoreProductRequest();
        request.setStorePrice(new BigDecimal("900.00"));

        assertDoesNotThrow(() -> service.updateStoreProduct(STORE, LISTING, request));

        assertTrue(warnings().stream().anyMatch(m -> m.contains("5000.00") && m.contains("900.00")),
                "lowering a listing under a priced variant reaches the identical state: " + warnings());
    }
}
