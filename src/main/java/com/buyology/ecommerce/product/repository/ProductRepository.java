package com.buyology.ecommerce.product.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.buyology.ecommerce.product.domain.Product;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    // Admin listing: paginated, optional status filter + free-text search over SKU
    // and any translation title. DISTINCT because the title join multiplies rows.
    @Query(value = """
            SELECT DISTINCT p FROM Product p
            LEFT JOIN ProductTranslation t ON t.product = p
            WHERE p.status <> 'DELETED'
              AND (:status IS NULL OR p.status = :status)
              AND (:q = ''
                   OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(t.title) LIKE LOWER(CONCAT('%', :q, '%')))
            """,
            countQuery = """
            SELECT COUNT(DISTINCT p) FROM Product p
            LEFT JOIN ProductTranslation t ON t.product = p
            WHERE p.status <> 'DELETED'
              AND (:status IS NULL OR p.status = :status)
              AND (:q = ''
                   OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(t.title) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<Product> searchAdmin(@Param("q") String q, @Param("status") String status, Pageable pageable);

    long countByStatusNot(String status);

    long countByStatus(String status);

    List<Product> findByCategoryId(UUID categoryId);

    List<Product> findByStatus(String status);

    List<Product> findByStatusAndCategoryId(String status, UUID categoryId);

    List<Product> findByStatusNot(String status);

    List<Product> findByStatusNotAndCategoryId(String status, UUID categoryId);

    /** Distinct category ids for the given products — used to enforce promo category exclusions. */
    @Query("SELECT DISTINCT p.category.id FROM Product p WHERE p.id IN :productIds")
    List<UUID> findCategoryIdsByProductIds(@Param("productIds") List<UUID> productIds);

    List<Product> findByStatusAndDeletedAtBefore(String status, Instant cutoff);

    boolean existsBySku(String sku);

    java.util.Optional<Product> findBySku(String sku);

    List<Product> findByStatusAndIsSuperDeal(String status, Boolean isSuperDeal);

    List<Product> findByStatusAndIsLimitedStock(String status, Boolean isLimitedStock);

    /** Distinct availability statuses present in active products. */
    @Query("SELECT DISTINCT p.availabilityStatus FROM Product p WHERE p.status = 'ACTIVE' AND p.availabilityStatus IS NOT NULL")
    List<Product.AvailabilityStatus> findDistinctAvailabilityStatuses();

    /** Whether any active product has isRefurbished = :value. */
    boolean existsByStatusAndIsRefurbished(String status, Boolean isRefurbished);

    // ── Stock ─────────────────────────────────────────────────────────────────

    /**
     * Takes {@code qty} units off a product's own stock, and refuses if they are not there.
     *
     * <p>The counterpart of {@code StoreProductVariantRepository.decrementStock} for products that
     * have no variants — which is every ERP-imported and spreadsheet-imported refurbished machine.
     * Until this existed those products had no stock check of any kind: {@code createOrder}
     * decremented them with {@code Math.max(0, ...)}, which by construction can never refuse a
     * sale, so a single physical laptop could be sold any number of times.
     *
     * <p>A statement rather than a read-modify-write on the entity, for two reasons. It is atomic
     * under READ COMMITTED — the {@code >= :qty} predicate is evaluated by Postgres while it holds
     * the row lock, so two concurrent orders for the last unit cannot both pass. And it composes
     * with {@link #incrementStock} when both run in one transaction, which happens in
     * {@code createOrder} where a stale order is cancelled (restoring units) and the fresh one
     * then takes them again.
     *
     * <p>Two states are deliberately not matched, so nothing that sells today stops selling.
     * {@code stockQuantity IS NULL} means "not tracked" — the meaning the column has always had.
     * And PRE_ORDER is an explicit instruction to accept orders that cannot be filled yet, which
     * is precisely a request to skip this check; it is also the default availability for a new
     * product, so guarding it would refuse pre-orders that work today. A product an admin wants
     * guarded is IN_STOCK.
     *
     * <p>{@link #incrementStock} carries the same PRE_ORDER predicate. That is what stops the
     * restore from inventing stock for a line the decrement never took anything from — the same
     * class of bug as the old floored-take/unfloored-restore asymmetry.
     *
     * <p>The PRE_ORDER test is spelled {@code (availabilityStatus IS NULL OR ... <> :preOrder)}
     * because this half of the guard is SQL and the caller's half is Java, where NULL does not
     * behave the same way. {@code null != PRE_ORDER} is true in Java, so the caller decides the
     * guard applies; {@code NULL <> 'PRE_ORDER'} is NULL in SQL, so the row matched nothing and the
     * caller read the zero row count as "not enough stock" — refusing every product whose
     * availability had never been set.
     *
     * @return 1 when the units were taken, 0 when there were not enough (or stock is untracked)
     */
    @Modifying
    @Query("update Product p set p.stockQuantity = p.stockQuantity - :qty " +
           "where p.id = :productId and p.stockQuantity is not null and p.stockQuantity >= :qty " +
           "and (p.availabilityStatus is null or p.availabilityStatus <> :preOrder)")
    int decrementStockIfAvailable(@Param("productId") UUID productId,
                                  @Param("qty") int qty,
                                  @Param("preOrder") Product.AvailabilityStatus preOrder);

    /**
     * Puts units back on a product whose order died.
     *
     * <p>No upper guard — returning units can never oversell. But note the asymmetry this fixes:
     * the decrement above refuses rather than flooring at zero, so the two are now exact mirrors.
     * While the decrement was floored and the restore was not, a declined card on a sold-out
     * product <em>invented</em> stock — it took one unit off a count already at 0 (no change) and
     * then gave one back.
     *
     * @return 1 when the product was found and tracks stock, 0 otherwise
     */
    @Modifying
    @Query("update Product p set p.stockQuantity = p.stockQuantity + :qty " +
           "where p.id = :productId and p.stockQuantity is not null " +
           "and (p.availabilityStatus is null or p.availabilityStatus <> :preOrder)")
    int incrementStock(@Param("productId") UUID productId,
                       @Param("qty") int qty,
                       @Param("preOrder") Product.AvailabilityStatus preOrder);

    /**
     * The legacy urgency-hint decrement: counts down, floors at zero, never refuses.
     *
     * <p>What {@code createOrder} does to {@code stockQuantity} while its guard is switched off, and
     * the behaviour the column has always had. Here as a statement rather than the read-modify-write
     * it used to be, for two reasons that are both live bugs.
     *
     * <p>It could not compose with {@link #incrementStock}. Buy Now supersedes a stale order —
     * restoring units with a bulk statement the persistence context cannot see — and then decrements
     * the SAME already-loaded Product instance from its stale pre-restore value, so the entity flush
     * at commit overwrote the restore. Net effect: every Buy Now retry sank the counter by an extra
     * unit, which is a good part of why this column cannot be trusted today. The normal cart path
     * escaped only because {@code CartItem.product} is LAZY and nothing dereferenced it early enough —
     * luck, not design.
     *
     * <p>And it carries the PRE_ORDER predicate, which the old inline version did not. The restore has
     * always had it, so the pair was asymmetric the other way: a PRE_ORDER product with a tracked
     * count WAS decremented and then refused its units back, losing them permanently on every
     * cancelled pre-order. Same class of bug as the floored-take/unfloored-restore asymmetry, opposite
     * direction. The two are exact mirrors now.
     *
     * @return 1 when the row was matched, 0 when untracked or PRE_ORDER
     */
    @Modifying
    @Query("update Product p set p.stockQuantity = "
           + "case when p.stockQuantity < :qty then 0 else p.stockQuantity - :qty end "
           + "where p.id = :productId and p.stockQuantity is not null "
           + "and (p.availabilityStatus is null or p.availabilityStatus <> :preOrder)")
    int decrementStockFlooredAtZero(@Param("productId") UUID productId,
                                    @Param("qty") int qty,
                                    @Param("preOrder") Product.AvailabilityStatus preOrder);

    default int decrementStockFlooredAtZero(UUID productId, int qty) {
        return decrementStockFlooredAtZero(productId, qty, Product.AvailabilityStatus.PRE_ORDER);
    }

    /** Keeps the PRE_ORDER argument out of every call site. */
    default int decrementStockIfAvailable(UUID productId, int qty) {
        return decrementStockIfAvailable(productId, qty, Product.AvailabilityStatus.PRE_ORDER);
    }

    default int incrementStock(UUID productId, int qty) {
        return incrementStock(productId, qty, Product.AvailabilityStatus.PRE_ORDER);
    }

    // ── available_quantity: the count that is allowed to refuse an order ─────────────────────────
    //
    // Separate statements from the stockQuantity pair above, on a separate column, because the two
    // numbers mean different things — see Product.availableQuantity and V55. In short: stockQuantity
    // is a display hint that has drifted for as long as it has existed, so enforcing it refused
    // checkout on the best-selling catalogue; availableQuantity is null everywhere until an admin
    // states a figure, so it can be enforced from the first deploy without refusing anything.

    /**
     * Takes {@code qty} units, and only if that many are there.
     *
     * <p>A conditional UPDATE, not a read-modify-write. That is the entire concurrency argument: the
     * {@code >= :qty} predicate is evaluated by Postgres while it holds the row lock, so two
     * customers buying the last unit cannot both succeed — one of them gets 0 rows back. Checking in
     * Java and then writing would let both read 2, both write 1, and sell one item twice.
     *
     * <p>It also has to be a statement rather than an entity mutation so it composes with
     * {@link #returnAvailableQuantity} inside a single transaction, which is exactly what happens in
     * {@code createOrder} when a stale order is cancelled (returning units) and the replacement then
     * takes them again. An entity write would be computed from the value loaded BEFORE the bulk
     * restore and would overwrite it on flush — the live Buy Now defect that makes the legacy
     * display counter sink by an extra unit on every retry.
     *
     * <p>Unlike {@link #decrementStockIfAvailable} this does NOT exempt PRE_ORDER: a number an admin
     * typed is a statement about units that exist, and PRE_ORDER is the default for a new product, so
     * exempting it would mean the count did nothing on most of the catalogue. Untracked is spelled
     * null, which the {@code is not null} predicate already excludes.
     *
     * @return 1 when the units were taken, 0 when there were not enough or the product is untracked
     */
    @Modifying
    @Query("update Product p set p.availableQuantity = p.availableQuantity - :qty " +
           "where p.id = :productId and p.availableQuantity is not null and p.availableQuantity >= :qty")
    int takeAvailableQuantity(@Param("productId") UUID productId, @Param("qty") int qty);

    /**
     * Puts units back when an order dies.
     *
     * <p>No upper guard: returning units cannot oversell. An exact mirror of
     * {@link #takeAvailableQuantity} — same column, same untracked predicate, no extra condition on
     * either side. That symmetry is the point. Every stock bug in this file's history has been an
     * asymmetry: a take that floored at zero paired with a restore that did not invented units, and a
     * take that ignored PRE_ORDER paired with a restore that honoured it loses them.
     *
     * @return 1 when the product was found and tracks a count, 0 otherwise
     */
    @Modifying
    @Query("update Product p set p.availableQuantity = p.availableQuantity + :qty " +
           "where p.id = :productId and p.availableQuantity is not null")
    int returnAvailableQuantity(@Param("productId") UUID productId, @Param("qty") int qty);

    /**
     * Marks a tracked product OUT_OF_STOCK once its last unit is sold.
     *
     * <p>Nothing did this before: {@code availabilityStatus} was set at import or by an admin edit
     * and then never recomputed, while the storefront derives its whole in-stock badge and its
     * Add to Cart gate from that field alone. A machine sold down to zero went on advertising
     * itself as in stock indefinitely.
     *
     * <p>Only flips a product that is currently IN_STOCK. A PRE_ORDER product is a deliberate
     * choice by an admin and is left alone.
     *
     * <p>The two statuses are bound as parameters rather than written as JPQL enum literals: a
     * nested enum has to be spelled with a {@code $} in a fully-qualified literal, which Hibernate
     * parses inconsistently across versions. The default methods below wrap this so callers never
     * have to pass them.
     */
    @Modifying
    @Query("update Product p set p.availabilityStatus = :outOfStock " +
           "where p.id = :productId and p.stockQuantity is not null and p.stockQuantity <= 0 " +
           "and p.availabilityStatus = :inStock")
    int markOutOfStockIfDepleted(@Param("productId") UUID productId,
                                 @Param("outOfStock") Product.AvailabilityStatus outOfStock,
                                 @Param("inStock") Product.AvailabilityStatus inStock);

    /** The mirror of {@link #markOutOfStockIfDepleted}, for when units come back. */
    @Modifying
    @Query("update Product p set p.availabilityStatus = :inStock " +
           "where p.id = :productId and p.stockQuantity is not null and p.stockQuantity > 0 " +
           "and p.availabilityStatus = :outOfStock")
    int markInStockIfReplenished(@Param("productId") UUID productId,
                                 @Param("inStock") Product.AvailabilityStatus inStock,
                                 @Param("outOfStock") Product.AvailabilityStatus outOfStock);

    /**
     * Marks a product out of stock once its STATED count reaches zero.
     *
     * <p>The available_quantity counterpart of {@link #markOutOfStockIfDepleted}, which keys on the
     * legacy stockQuantity column and therefore never fired for a product whose real count ran out.
     * Without this the storefront went on showing a sold-out product as available with a live Add to
     * Cart button — it derives that badge from availabilityStatus alone — and the customer only found
     * out when the add was refused.
     *
     * <p>Moves only IN_STOCK to OUT_OF_STOCK. PRE_ORDER is left alone: it means "accept orders we
     * cannot fill yet", and flipping it would contradict the order path, which DOES still sell a
     * pre-order product against its stated count.
     */
    @Modifying
    @Query("update Product p set p.availabilityStatus = :outOfStock " +
           "where p.id = :productId and p.availableQuantity is not null and p.availableQuantity <= 0 " +
           "and p.availabilityStatus = :inStock")
    int markOutOfStockIfAvailableDepleted(@Param("productId") UUID productId,
                                          @Param("outOfStock") Product.AvailabilityStatus outOfStock,
                                          @Param("inStock") Product.AvailabilityStatus inStock);

    /** The mirror, for when units are returned by a cancellation. */
    @Modifying
    @Query("update Product p set p.availabilityStatus = :inStock " +
           "where p.id = :productId and p.availableQuantity is not null and p.availableQuantity > 0 " +
           "and p.availabilityStatus = :outOfStock")
    int markInStockIfAvailableReplenished(@Param("productId") UUID productId,
                                          @Param("inStock") Product.AvailabilityStatus inStock,
                                          @Param("outOfStock") Product.AvailabilityStatus outOfStock);

    default int markOutOfStockIfAvailableDepleted(UUID productId) {
        return markOutOfStockIfAvailableDepleted(productId,
                Product.AvailabilityStatus.OUT_OF_STOCK, Product.AvailabilityStatus.IN_STOCK);
    }

    default int markInStockIfAvailableReplenished(UUID productId) {
        return markInStockIfAvailableReplenished(productId,
                Product.AvailabilityStatus.IN_STOCK, Product.AvailabilityStatus.OUT_OF_STOCK);
    }

    /** Keeps the enum arguments out of every call site. */
    default int markOutOfStockIfDepleted(UUID productId) {
        return markOutOfStockIfDepleted(productId,
                Product.AvailabilityStatus.OUT_OF_STOCK, Product.AvailabilityStatus.IN_STOCK);
    }

    default int markInStockIfReplenished(UUID productId) {
        return markInStockIfReplenished(productId,
                Product.AvailabilityStatus.IN_STOCK, Product.AvailabilityStatus.OUT_OF_STOCK);
    }

    // ── Bulk import ───────────────────────────────────────────────────────────

    /** The fulfilment queue: imported products still waiting on a human and their photos. */
    List<Product> findByNeedsFulfilmentTrueAndStatusNot(String status);

    List<Product> findByImportJobId(UUID importJobId);

    // ── Supplier product queries ───────────────────────────────────────────────
    Page<Product> findBySupplierId(UUID supplierId, Pageable pageable);
    Page<Product> findBySupplierIdIsNotNull(Pageable pageable);
    Page<Product> findBySupplierIdAndSupplierStatus(UUID supplierId, Product.SupplierStatus supplierStatus, Pageable pageable);
    Page<Product> findBySupplierStatus(Product.SupplierStatus supplierStatus, Pageable pageable);
}
