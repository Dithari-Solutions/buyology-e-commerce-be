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
}
