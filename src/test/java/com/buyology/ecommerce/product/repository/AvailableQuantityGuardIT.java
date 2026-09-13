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
 * Pins {@code products.available_quantity} — the count that is allowed to refuse an order.
 *
 * <p>Separate from {@link ProductStockGuardIT}, which pins the legacy {@code stock_quantity} guard,
 * because the two columns answer different questions and their rules differ on purpose. The important
 * difference is PRE_ORDER: the legacy guard exempts it, this one does not, because a number an admin
 * typed is a statement about units that exist and PRE_ORDER is the DEFAULT for a new product — so
 * exempting it would mean the count did nothing on most of the catalogue. See V55.
 *
 * <p>The assertions that matter are the refusals, and every one checks both the returned row count and
 * that the stored number did not move. It is easy to write a decrement that works on the happy path
 * and quietly sells stock that is not there; that is the whole failure mode being guarded against.
 *
 * <p>Against a real Postgres, not H2, because the safety argument is entirely about how Postgres
 * evaluates the {@code >= :qty} predicate while holding the row lock.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Testcontainers
class AvailableQuantityGuardIT {

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

        assertEquals(0, productRepository.takeAvailableQuantity(id, 2),
                "asking for 2 units of a 1-unit product must be refused");
        assertEquals(1, availableOf(id), "a refused take must not move the count");
    }

    @Test
    void refusesWhenNothingIsLeft() {
        UUID id = product(0, Product.AvailabilityStatus.IN_STOCK);

        assertEquals(0, productRepository.takeAvailableQuantity(id, 1));
        assertEquals(0, availableOf(id), "must not go negative");
    }

    @Test
    void sellsTheLastUnitButNotTheOneAfterIt() {
        UUID id = product(1, Product.AvailabilityStatus.IN_STOCK);

        assertEquals(1, productRepository.takeAvailableQuantity(id, 1), "the last unit is sellable");
        assertEquals(0, availableOf(id));

        assertEquals(0, productRepository.takeAvailableQuantity(id, 1),
                "the same physical item must not sell twice");
        assertEquals(0, availableOf(id));
    }

    @Test
    void takesExactlyWhatWasAskedFor() {
        UUID id = product(10, Product.AvailabilityStatus.IN_STOCK);

        assertEquals(1, productRepository.takeAvailableQuantity(id, 3));
        assertEquals(7, availableOf(id));
    }

    // ─── Untracked means no ceiling ───────────────────────────────────────────

    @Test
    void anUntrackedProductIsNotMatchedAndKeepsSelling() {
        // Null is the state every existing row is in, and the reason enforcement could be switched on
        // without refusing a single order. If the guard read null as zero, the entire catalogue would
        // have stopped selling the moment this deployed — a far worse outage than the bug it fixes.
        UUID id = product(null, Product.AvailabilityStatus.IN_STOCK);

        assertEquals(0, productRepository.takeAvailableQuantity(id, 500),
                "an untracked product is not matched, so nothing refuses it");
        assertNull(availableOf(id), "and it is left untouched");
    }

    @Test
    void restoringAnUntrackedProductDoesNotInventACount() {
        // The mirror of the above: if the restore treated null as 0 it would fabricate a ceiling out of
        // a cancelled order, and a product that had always sold freely would suddenly have a limit.
        UUID id = product(null, Product.AvailabilityStatus.IN_STOCK);

        assertEquals(0, productRepository.returnAvailableQuantity(id, 3));
        assertNull(availableOf(id));
    }

    // ─── PRE_ORDER is NOT exempt here, unlike the legacy column ───────────────

    @Test
    void aPreOrderProductWithAStatedCountIsStillGuarded() {
        // The legacy stock_quantity guard exempts PRE_ORDER, on the grounds that "accept orders we
        // cannot fill yet" is an instruction to skip a stock check. That reasoning does not survive an
        // admin typing an explicit number — and PRE_ORDER is the default availability for a new
        // product, so exempting it would have made this whole column do nothing on most of the
        // catalogue. Untracked is spelled null; that is what null is for.
        UUID id = product(1, Product.AvailabilityStatus.PRE_ORDER);

        assertEquals(1, productRepository.takeAvailableQuantity(id, 1));
        assertEquals(0, productRepository.takeAvailableQuantity(id, 1),
                "a stated count limits a pre-order product too");
        assertEquals(0, availableOf(id));
    }

    @Test
    void aProductWithNoAvailabilityStatusIsGuardedNormally() {
        // Rows predating the availability column have NULL there. The legacy guard needed an explicit
        // `IS NULL OR <> PRE_ORDER` spelling to cope, because `NULL <> 'PRE_ORDER'` is NULL in SQL and
        // matched nothing — which refused every such product. This statement carries no availability
        // predicate at all, so there is nothing to get wrong; pinned so it stays that way.
        UUID id = productWithNullAvailability(2);

        assertEquals(1, productRepository.takeAvailableQuantity(id, 2));
        assertEquals(0, availableOf(id));
    }

    // ─── Take and restore must be exact mirrors ───────────────────────────────

    @Test
    void restoringPutsBackExactlyWhatWasTaken() {
        UUID id = product(5, Product.AvailabilityStatus.IN_STOCK);

        productRepository.takeAvailableQuantity(id, 2);
        assertEquals(3, availableOf(id));

        assertEquals(1, productRepository.returnAvailableQuantity(id, 2));
        assertEquals(5, availableOf(id), "a cancelled order leaves the count where it started");
    }

    @Test
    void aRefusedSaleFollowedByARestoreDoesNotInventStock() {
        // The shape of every stock bug in this file's history. While a take floored at zero and its
        // restore did not, a declined card on a sold-out product INVENTED a unit: it subtracted from a
        // count already at 0 (no change) and then added one back. This take refuses instead of
        // flooring, so the pair cannot drift.
        UUID id = product(0, Product.AvailabilityStatus.IN_STOCK);

        assertEquals(0, productRepository.takeAvailableQuantity(id, 1), "nothing was taken");
        assertEquals(0, availableOf(id));
    }

    @Test
    void aPreOrderProductGetsItsUnitsBackToo() {
        // The asymmetry that loses units: a take that ignores PRE_ORDER paired with a restore that
        // honours it means a cancelled pre-order never gets its units back. Neither statement carries
        // the predicate, so they agree.
        UUID id = product(3, Product.AvailabilityStatus.PRE_ORDER);

        assertEquals(1, productRepository.takeAvailableQuantity(id, 2));
        assertEquals(1, availableOf(id));
        assertEquals(1, productRepository.returnAvailableQuantity(id, 2));
        assertEquals(3, availableOf(id));
    }

    // ─── The two columns are independent ──────────────────────────────────────

    @Test
    void takingAvailableUnitsLeavesTheLegacyUrgencyHintAlone() {
        // They are separate columns on purpose: one is a number somebody vouches for, the other has
        // been drifting since V12. A change to either must not move the other.
        UUID id = product(4, Product.AvailabilityStatus.IN_STOCK);
        setLegacyStock(id, 4);

        productRepository.takeAvailableQuantity(id, 1);

        assertEquals(3, availableOf(id));
        assertEquals(4, legacyStockOf(id), "the urgency hint is not this statement's business");
    }

    @Test
    void theLegacyGuardDoesNotTouchTheStatedCount() {
        UUID id = product(4, Product.AvailabilityStatus.IN_STOCK);
        setLegacyStock(id, 4);

        productRepository.decrementStockIfAvailable(id, 1);

        assertEquals(3, legacyStockOf(id));
        assertEquals(4, availableOf(id), "the stated count is not the legacy guard's business");
    }

    // ─── The legacy floored decrement, now a statement ────────────────────────

    @Test
    void theFlooredDecrementStillFloorsAtZeroAndNeverRefuses() {
        // Historical behaviour, preserved exactly — it is what keeps the "almost sold out" message
        // working while the legacy guard is off. Moved from a write on the loaded entity to a
        // statement so it stops overwriting a bulk restore made earlier in the same transaction.
        UUID id = product(null, Product.AvailabilityStatus.IN_STOCK);
        setLegacyStock(id, 2);

        assertEquals(1, productRepository.decrementStockFlooredAtZero(id, 5),
                "it never refuses — that is the point of it");
        assertEquals(0, legacyStockOf(id), "and it floors rather than going negative");
    }

    @Test
    void theFlooredDecrementLeavesPreOrderAlone() {
        // The restore has always carried this predicate. Without it here, the pair was asymmetric the
        // other way round: a PRE_ORDER product WAS decremented and then refused its units back, so
        // every cancelled pre-order lost them permanently.
        UUID id = product(null, Product.AvailabilityStatus.PRE_ORDER);
        setLegacyStock(id, 2);

        assertEquals(0, productRepository.decrementStockFlooredAtZero(id, 1));
        assertEquals(2, legacyStockOf(id));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private UUID product(Integer availableQuantity, Product.AvailabilityStatus availability) {
        ProductCategory category = new ProductCategory();
        category.setStatus("ACTIVE");
        category = categoryRepository.save(category);

        Product p = new Product(
                category, null, Product.ProductType.SIMPLE, false, null,
                "SKU-" + UUID.randomUUID(), "ACTIVE", availability, false, false);
        p.setAvailableQuantity(availableQuantity);
        Product saved = productRepository.saveAndFlush(p);
        em.clear();   // the statements under test are bulk updates; read back from the database
        return saved.getId();
    }

    /**
     * A product whose availability_status is NULL — the state rows are in when they predate the
     * column. The entity defaults it to PRE_ORDER, so it is nulled directly.
     */
    private UUID productWithNullAvailability(Integer availableQuantity) {
        UUID id = product(availableQuantity, Product.AvailabilityStatus.IN_STOCK);
        em.createNativeQuery("update products set availability_status = null where id = :id")
                .setParameter("id", id)
                .executeUpdate();
        em.flush();
        em.clear();
        return id;
    }

    private void setLegacyStock(UUID id, Integer stock) {
        em.createNativeQuery("update products set stock_quantity = :stock where id = :id")
                .setParameter("stock", stock)
                .setParameter("id", id)
                .executeUpdate();
        em.flush();
        em.clear();
    }

    private Integer availableOf(UUID id) {
        em.clear();
        return productRepository.findById(id).orElseThrow().getAvailableQuantity();
    }

    private Integer legacyStockOf(UUID id) {
        em.clear();
        return productRepository.findById(id).orElseThrow().getStockQuantity();
    }
}
