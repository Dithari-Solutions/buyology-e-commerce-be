package com.buyology.ecommerce.b2b.quote.repository;

import com.buyology.ecommerce.b2b.quote.domain.B2bQuoteItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface B2bQuoteItemRepository extends JpaRepository<B2bQuoteItem, UUID> {

    List<B2bQuoteItem> findByQuote_IdOrderByCreatedAtAsc(UUID quoteId);

    /** Upsert key — the unique (quote, store_product, variant) line. */
    Optional<B2bQuoteItem> findByQuote_IdAndStoreProductIdAndVariantId(
            UUID quoteId, UUID storeProductId, UUID variantId);

    long countByQuote_Id(UUID quoteId);
}
