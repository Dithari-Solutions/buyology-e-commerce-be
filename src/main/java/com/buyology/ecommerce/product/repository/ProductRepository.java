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
     * <p>{@code stockQuantity IS NULL} means "not tracked" — the meaning the column has always
     * had — and such a product is deliberately not matched here, so untracked products stay
     * sellable exactly as before.
     *
     * @return 1 when the units were taken, 0 when there were not enough (or stock is untracked)
     */
    @Modifying
    @Query("update Product p set p.stockQuantity = p.stockQuantity - :qty " +
           "where p.id = :productId and p.stockQuantity is not null and p.stockQuantity >= :qty")
    int decrementStockIfAvailable(@Param("productId") UUID productId, @Param("qty") int qty);

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
           "where p.id = :productId and p.stockQuantity is not null")
    int incrementStock(@Param("productId") UUID productId, @Param("qty") int qty);

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
