package com.buyology.ecommerce.product.search.service;

import com.buyology.ecommerce.product.repository.BrandTranslationRepository;
import com.buyology.ecommerce.product.repository.ProductCategoryTranslationRepository;
import com.buyology.ecommerce.product.search.domain.ProductDocument;
import com.buyology.ecommerce.product.search.repository.ProductSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHits;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pins the search query a shopper's words turn into.
 *
 * <p>Typing "macbook" on a shop with four MacBooks in stock returned nothing. The query asked for
 * no particular number of hits, so Elasticsearch returned its default ten, and the caller then
 * dropped the ones that were not active or not sold here — after the cut. Nothing said "only
 * active products", and "mac" could never reach "macbook", because fuzziness allows no edits on a
 * word that short.
 */
class ProductSearchQueryTest {

    private ElasticsearchOperations operations;
    private ProductSearchService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        operations = mock(ElasticsearchOperations.class);
        IndexOperations indexOps = mock(IndexOperations.class);
        when(operations.indexOps(ProductDocument.class)).thenReturn(indexOps);
        when(indexOps.exists()).thenReturn(true);
        SearchHits<ProductDocument> empty = mock(SearchHits.class);
        when(empty.getSearchHits()).thenReturn(List.of());
        when(operations.search(any(NativeQuery.class), eq(ProductDocument.class))).thenReturn(empty);

        service = new ProductSearchService(
                mock(ProductSearchRepository.class), operations,
                mock(BrandTranslationRepository.class), mock(ProductCategoryTranslationRepository.class));
    }

    private String queryFor(String text) {
        service.search(text);
        ArgumentCaptor<NativeQuery> captor = ArgumentCaptor.forClass(NativeQuery.class);
        verify(operations).search(captor.capture(), eq(ProductDocument.class));
        NativeQuery sent = captor.getValue();
        assertNotNull(sent.getPageable(), "a query with no size gets Elasticsearch's default ten");
        assertTrue(sent.getPageable().getPageSize() >= 100,
                "the hits are filtered by country and status afterwards, so ask for plenty");
        return String.valueOf(sent.getQuery());
    }

    @Test
    void asksForEnoughHitsAndOnlyForProductsThatCanBeBought() {
        String query = queryFor("macbook");

        assertTrue(query.contains("ACTIVE"), "inactive documents used to fill the ten slots: " + query);
        assertTrue(query.contains("status"), query);
    }

    @Test
    void matchesTheStartOfAWordSoMacFindsMacBook() {
        String query = queryFor("mac");

        // A prefix match, not fuzziness: "mac" is four edits from "macbook", and AUTO fuzziness
        // allows none at three characters.
        assertTrue(query.toLowerCase().contains("prefix"), query);
        assertTrue(query.contains("translations.title^4"), query);
    }

    @Test
    void stillForgivesATypo() {
        String query = queryFor("macbok");

        assertTrue(query.contains("AUTO"), "fuzziness covers the typos prefix matching cannot: " + query);
    }

    @Test
    void alsoLooksAtTheCategoryBrandAndSku() {
        String query = queryFor("lenovo");

        assertTrue(query.contains("brandName"), query);
        assertTrue(query.contains("categoryName"), query);
        assertTrue(query.contains("sku"), query);
    }

    @Test
    void emptyWordsAskElasticsearchNothingAtAll() {
        assertTrue(service.search("   ").isEmpty());
        assertTrue(service.search(null).isEmpty());
        verify(operations, never()).search(any(NativeQuery.class), eq(ProductDocument.class));
    }
}
