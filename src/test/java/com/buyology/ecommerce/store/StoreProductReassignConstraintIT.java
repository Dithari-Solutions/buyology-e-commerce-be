package com.buyology.ecommerce.store;

import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.product.domain.ProductCategory;
import com.buyology.ecommerce.product.repository.ProductCategoryRepository;
import com.buyology.ecommerce.product.repository.ProductRepository;
import com.buyology.ecommerce.store.domain.Store;
import com.buyology.ecommerce.store.domain.StoreProduct;
import com.buyology.ecommerce.store.repository.StoreProductRepository;
import com.buyology.ecommerce.store.repository.StoreRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Why assigning a product to a store has to REVIVE a removed assignment rather than insert a new one.
 *
 * <p>The reported bug: an admin removes a product from a store and can never add it back — the dashboard
 * answers "A record with the same unique value already exists". Two facts combine to produce that, and
 * this test pins both so neither can be changed in isolation:
 *
 * <ol>
 *   <li>removal is a SOFT delete (the row stays, with {@code deleted_at} stamped), because
 *       {@code b2b_quote_items.store_product_id} points at it with no foreign key — destroying the row
 *       would silently orphan quote lines;</li>
 *   <li>{@code UNIQUE (store_id, product_id)} is unconditional, so the tombstone occupies the pair
 *       forever and a second INSERT for it is rejected by Postgres.</li>
 * </ol>
 *
 * <p>Against a real Postgres rather than H2 on purpose: the whole point is what the DATABASE does with a
 * duplicate key, which an in-memory stand-in is free to model differently.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Testcontainers
class StoreProductReassignConstraintIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    StoreProductRepository storeProductRepository;

    @Autowired
    StoreRepository storeRepository;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    ProductCategoryRepository categoryRepository;

    @Autowired
    EntityManager em;

    @Test
    void aSoftDeletedAssignmentStillOccupiesTheUniquePair() {
        // The failure the admin hit, reproduced at the level that actually causes it. If this ever stops
        // throwing, the constraint has been relaxed and reviving is no longer the only correct fix — at
        // which point the duplicate-row consequences for b2b_quote_items need thinking about again.
        Fixture f = fixture();
        StoreProduct first = storeProductRepository.saveAndFlush(
                new StoreProduct(f.store, f.product, new BigDecimal("100.00")));

        first.setIsActive(false);
        first.setDeletedAt(java.time.Instant.now());
        storeProductRepository.saveAndFlush(first);
        em.clear();

        assertThrows(DataIntegrityViolationException.class, () -> storeProductRepository.saveAndFlush(
                        new StoreProduct(f.store, f.product, new BigDecimal("120.00"))),
                "a removed assignment still holds (store_id, product_id), so re-inserting must be refused "
                        + "— which is why assignProduct revives instead of inserting");
    }

    @Test
    void theRemovedRowIsInvisibleToTheLookupThatUsedToGuardAssignment() {
        // The other half of the bug: the guard asked for ACTIVE rows only, so it saw nothing and let the
        // doomed insert through. The unfiltered lookup is what makes reviving possible.
        Fixture f = fixture();
        StoreProduct sp = storeProductRepository.saveAndFlush(
                new StoreProduct(f.store, f.product, new BigDecimal("100.00")));
        sp.setIsActive(false);
        sp.setDeletedAt(java.time.Instant.now());
        storeProductRepository.saveAndFlush(sp);
        em.clear();

        assertTrue(storeProductRepository
                        .findByStore_IdAndProduct_IdAndIsActiveTrue(f.store.getId(), f.product.getId())
                        .isEmpty(),
                "the active-only lookup cannot see a removed assignment");
        assertTrue(storeProductRepository
                        .findByStore_IdAndProduct_Id(f.store.getId(), f.product.getId())
                        .isPresent(),
                "the unfiltered lookup must see it, or assignment cannot revive it");
    }

    @Test
    void revivingTheSameRowIsAcceptedAndKeepsItsIdentity() {
        // What the fix does instead. Keeping the same id is the point, not an incidental detail:
        // b2b_quote_items reference it.
        Fixture f = fixture();
        StoreProduct sp = storeProductRepository.saveAndFlush(
                new StoreProduct(f.store, f.product, new BigDecimal("100.00")));
        UUID originalId = sp.getId();

        sp.setIsActive(false);
        sp.setDeletedAt(java.time.Instant.now());
        storeProductRepository.saveAndFlush(sp);
        em.clear();

        StoreProduct revived = storeProductRepository
                .findByStore_IdAndProduct_Id(f.store.getId(), f.product.getId()).orElseThrow();
        revived.setDeletedAt(null);
        revived.setIsActive(true);
        revived.setStorePrice(new BigDecimal("120.00"));
        storeProductRepository.saveAndFlush(revived);
        em.clear();

        StoreProduct after = storeProductRepository.findById(originalId).orElseThrow();
        assertNull(after.getDeletedAt());
        assertTrue(after.getIsActive());
        assertEquals(0, new BigDecimal("120.00").compareTo(after.getStorePrice()),
                "a re-assignment takes the new price, not the one it was removed with");
        assertEquals(1, storeProductRepository.findByStore_IdAndDeletedAtIsNull(f.store.getId()).size(),
                "and there is exactly one live assignment, not two");
    }

    // ─── Fixture ──────────────────────────────────────────────────────────────

    private record Fixture(Store store, Product product) {}

    private Fixture fixture() {
        Store store = new Store();
        store.setName("Store " + UUID.randomUUID());
        store = storeRepository.saveAndFlush(store);

        ProductCategory category = new ProductCategory();
        category.setStatus("ACTIVE");
        category = categoryRepository.saveAndFlush(category);

        Product product = new Product(
                category, null, Product.ProductType.SIMPLE, false, null,
                "SKU-" + UUID.randomUUID(), "ACTIVE", Product.AvailabilityStatus.IN_STOCK, false, false);
        product = productRepository.saveAndFlush(product);

        em.clear();
        return new Fixture(store, product);
    }
}
