package com.buyology.ecommerce.store.repository;

import com.buyology.ecommerce.store.domain.StoreProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StoreProductVariantRepository extends JpaRepository<StoreProductVariant, UUID> {

    Optional<StoreProductVariant> findByStoreProduct_IdAndVariant_Id(UUID storeProductId, UUID variantId);

    List<StoreProductVariant> findByStoreProduct_Id(UUID storeProductId);

    /**
     * Atomically decrements stock for the store-listing of the given (store, product, variant),
     * guarding against overselling: the row is only updated when {@code stock >= qty}.
     * Returns the number of rows affected — 1 on success, 0 if insufficient stock or no match.
     */
    @Modifying
    @Query("update StoreProductVariant v set v.stock = v.stock - :qty " +
           "where v.variant.id = :variantId " +
           "and v.storeProduct.product.id = :productId " +
           "and v.storeProduct.store.id = :storeId " +
           "and v.stock >= :qty")
    int decrementStock(@Param("storeId") UUID storeId,
                       @Param("productId") UUID productId,
                       @Param("variantId") UUID variantId,
                       @Param("qty") int qty);

    /**
     * Puts stock back for the store-listing of the given (store, product, variant) when the order
     * that reserved it dies.
     *
     * <p>The mirror of {@link #decrementStock}, and deliberately a statement rather than a
     * read-modify-write on the entity: the decrement is a statement too, so the two compose
     * correctly when both run in one transaction — which happens in createOrder, where a stale
     * order is cancelled and the fresh one then decrements the same row.
     *
     * <p>No lower guard, unlike the decrement: putting units back can never oversell.
     *
     * @return 1 when the listing was found, 0 when it no longer exists (delisted since the order)
     */
    @Modifying
    @Query("update StoreProductVariant v set v.stock = v.stock + :qty " +
           "where v.variant.id = :variantId " +
           "and v.storeProduct.product.id = :productId " +
           "and v.storeProduct.store.id = :storeId")
    int incrementStock(@Param("storeId") UUID storeId,
                       @Param("productId") UUID productId,
                       @Param("variantId") UUID variantId,
                       @Param("qty") int qty);
}
