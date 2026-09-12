package com.buyology.ecommerce.product.repository;

import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.product.domain.ProductCategory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.persistence.EntityManager;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the oversell guard for products that have no variants.
 *
 * <p>This is the path that had no guard at all: {@code OrderService.createOrder} only ever checked
 * stock on {@code StoreProductVariant}, and a cart line without a variant — every ERP- and
 * spreadsheet-imported refurbished machine, and every Buy Now order, which always builds its item
 * with a null variant — was decremented with {@code Math.max(0, ...)}, which by construction
 * cannot refuse a sale. One physical laptop could be sold any number of times.
 *
 * <p>The assertions that matter are the refusals. It is easy to write a decrement that works on the
 * happy path and silently sells stock that is not there, and that is exactly the bug being fixed,
 * so each test below checks both the returned row count <em>and</em> that the stored number did not
 * move.
 *
 * <p>Runs against a real Postgres rather than H2 because the guard is a conditional UPDATE whose
 * whole safety argument is how Postgres evaluates the predicate under a row lock.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Testcontainers
class ProductStockGuardIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductCategoryRepository categoryRepository;

    @Autowired
    private EntityManager em;

    // ─── The refusals ─────────────────────────────────────────────────────────

    @Test
    void refusesToSellMoreUnitsThanExist() {
        UUID id = product(1, Product.AvailabilityStatus.IN_STOCK);

        assertEquals(0, productRepository.decrementStockIfAvailable(id, 2),
                "asking for 2 units of a 1-unit product must be refused");
        assertEquals(1, stockOf(id), "a refused decrement must not move the count");
    }

    @Test
    void refusesWhenNothingIsLeft() {
        UUID id = product(0, Product.AvailabilityStatus.IN_STOCK);

        assertEquals(0, productRepository.decrementStockIfAvailable(id, 1));
        assertEquals(0, stockOf(id), "must not go negative");
    }

    @Test
    void sellsTheLastUnitButNotTheOneAfterIt() {
        UUID id = product(1, Product.AvailabilityStatus.IN_STOCK);

        assertEquals(1, productRepository.decrementStockIfAvailable(id, 1), "the last unit is sellable");
        assertEquals(0, stockOf(id));

        // The second customer. Before the fix this succeeded, twice over: the floor at zero meant
        // the count stayed at 0 and the order went through anyway.
        assertEquals(0, productRepository.decrementStockIfAvailable(id, 1),
                "the same physical machine must not sell twice");
        assertEquals(0, stockOf(id));
    }

    // ─── Untracked stock stays sellable ───────────────────────────────────────

    @Test
    void aProductThatDoesNotTrackStockIsNotMatched() {
        // null has always meant "not tracked" on this column. If the guard treated it as zero,
        // every product that has never had a stock number entered would stop selling the moment
        // this shipped — which is a worse outage than the bug it fixes.
        UUID id = product(null, Product.AvailabilityStatus.IN_STOCK);

        assertEquals(0, productRepository.decrementStockIfAvailable(id, 5),
                "an untracked product is not matched by the guard");
        assertNull(stockOf(id), "and is left untouched");
    }

    // ─── The restore is a true mirror ─────────────────────────────────────────

    @Test
    void restoringPutsBackExactlyWhatWasTaken() {
        UUID id = product(3, Product.AvailabilityStatus.IN_STOCK);

        assertEquals(1, productRepository.decrementStockIfAvailable(id, 2));
        assertEquals(1, stockOf(id));

        assertEquals(1, productRepository.incrementStock(id, 2));
        assertEquals(3, stockOf(id), "a cancelled order returns the units it held");
    }

    @Test
    void aRefusedSaleFollowedByARestoreDoesNotInventStock() {
        // The old asymmetry: the decrement was floored at 0 and the restore was not, so a declined
        // card on a sold-out product subtracted nothing and then added one back — conjuring a unit
        // of inventory that does not physically exist. The decrement now refuses, so the restore
        // never runs for a line that took nothing.
        UUID id = product(0, Product.AvailabilityStatus.IN_STOCK);

        int taken = productRepository.decrementStockIfAvailable(id, 1);
        assertEquals(0, taken);
        if (taken == 1) {
            productRepository.incrementStock(id, 1);
        }
        assertEquals(0, stockOf(id), "a sale that never happened must not create stock");
    }

    // ─── Availability follows the count ───────────────────────────────────────

    @Test
    void sellingTheLastUnitMarksTheProductOutOfStock() {
        // Nothing recomputed availabilityStatus before, and the storefront derives its in-stock
        // badge and its Add to Cart gate from that field alone — so a sold-out machine went on
        // advertising itself as available indefinitely.
        UUID id = product(1, Product.AvailabilityStatus.IN_STOCK);

        productRepository.decrementStockIfAvailable(id, 1);
        assertEquals(1, productRepository.markOutOfStockIfDepleted(id));
        assertEquals(Product.AvailabilityStatus.OUT_OF_STOCK, availabilityOf(id));
    }

    @Test
    void aProductWithUnitsLeftIsNotMarkedOutOfStock() {
        UUID id = product(5, Product.AvailabilityStatus.IN_STOCK);

        productRepository.decrementStockIfAvailable(id, 1);
        assertEquals(0, productRepository.markOutOfStockIfDepleted(id));
        assertEquals(Product.AvailabilityStatus.IN_STOCK, availabilityOf(id));
    }

    @Test
    void preOrderIsLeftAlone() {
        // PRE_ORDER is a deliberate choice by an admin — selling a unit must not silently convert
        // a pre-order listing into an out-of-stock one.
        UUID id = product(1, Product.AvailabilityStatus.PRE_ORDER);

        productRepository.decrementStockIfAvailable(id, 1);
        assertEquals(0, productRepository.markOutOfStockIfDepleted(id));
        assertEquals(Product.AvailabilityStatus.PRE_ORDER, availabilityOf(id));
    }

    @Test
    void restockingBringsAProductBack() {
        UUID id = product(0, Product.AvailabilityStatus.OUT_OF_STOCK);

        productRepository.incrementStock(id, 2);
        assertEquals(1, productRepository.markInStockIfReplenished(id));
        assertEquals(Product.AvailabilityStatus.IN_STOCK, availabilityOf(id));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private UUID product(Integer stock, Product.AvailabilityStatus availability) {
        ProductCategory category = new ProductCategory();
        category.setStatus("ACTIVE");
        category = categoryRepository.save(category);

        Product p = new Product(
                category, null, Product.ProductType.SIMPLE, false, null,
                "SKU-" + UUID.randomUUID(), "ACTIVE", availability, false, false);
        p.setStockQuantity(stock);
        Product saved = productRepository.saveAndFlush(p);
        em.clear();   // the queries under test are bulk statements; read back from the database
        return saved.getId();
    }

    private Integer stockOf(UUID id) {
        em.clear();
        return productRepository.findById(id).orElseThrow().getStockQuantity();
    }

    private Product.AvailabilityStatus availabilityOf(UUID id) {
        em.clear();
        return productRepository.findById(id).orElseThrow().getAvailabilityStatus();
    }
}
