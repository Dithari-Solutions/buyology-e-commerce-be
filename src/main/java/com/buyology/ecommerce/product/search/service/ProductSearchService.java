package com.buyology.ecommerce.product.search.service;

import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.product.domain.ProductTranslation;
import com.buyology.ecommerce.product.repository.BrandTranslationRepository;
import com.buyology.ecommerce.product.repository.ProductCategoryTranslationRepository;
import com.buyology.ecommerce.product.search.domain.ProductDocument;
import com.buyology.ecommerce.product.search.repository.ProductSearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.elasticsearch.NoSuchIndexException;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductSearchService {

    private static final Logger log = LoggerFactory.getLogger(ProductSearchService.class);

    private final ProductSearchRepository productSearchRepository;
    private final ElasticsearchOperations elasticsearchOperations;
    private final BrandTranslationRepository brandTranslationRepository;
    private final ProductCategoryTranslationRepository categoryTranslationRepository;

    public ProductSearchService(
            ProductSearchRepository productSearchRepository,
            ElasticsearchOperations elasticsearchOperations,
            BrandTranslationRepository brandTranslationRepository,
            ProductCategoryTranslationRepository categoryTranslationRepository) {
        this.productSearchRepository = productSearchRepository;
        this.elasticsearchOperations = elasticsearchOperations;
        this.brandTranslationRepository = brandTranslationRepository;
        this.categoryTranslationRepository = categoryTranslationRepository;
        ensureIndexExists();
    }

    private void ensureIndexExists() {
        IndexOperations indexOps = elasticsearchOperations.indexOps(ProductDocument.class);
        if (!indexOps.exists()) {
            indexOps.createWithMapping();
            log.info("[ES] Created 'products' index with mapping");
        }
    }

    /**
     * Indexes a product for search, unless it is a draft.
     *
     * <p>Search is a second, independent way a product reaches a customer, and this query applies
     * no status filter of its own — so without this guard a bulk-imported DRAFT product stays off
     * the category pages but turns up in the search box, which is the worse failure of the two
     * because it looks deliberate. Delisting on the way in rather than filtering on the way out
     * keeps the index honest: a document that is in it is a product that may be sold.
     */
    public void indexProduct(Product product, List<ProductTranslation> translations) {
        if (product != null && "DRAFT".equals(product.getStatus())) {
            // Also remove any document from a previous publish, so demoting a product to DRAFT
            // actually takes it out of search rather than leaving the old copy behind.
            deleteProduct(product);
            return;
        }
        ProductDocument doc = mapToDocument(product, translations);
        productSearchRepository.save(doc);
    }

    public void reindexAll(List<Product> products, java.util.function.Function<Product, List<ProductTranslation>> translationLoader) {
        long count = productSearchRepository.count();
        if (count > 0) {
            log.info("[ES] Index already has {} documents, skipping reindex. Use manual trigger if needed.", count);
            return;
        }
        forceReindex(products, translationLoader);
    }

    public void forceReindex(List<Product> products, java.util.function.Function<Product, List<ProductTranslation>> translationLoader) {
        log.info("[ES] Force reindexing {} products into Elasticsearch", products.size());
        productSearchRepository.deleteAll();
        List<ProductDocument> docs = products.stream()
                .map(p -> mapToDocument(p, translationLoader.apply(p)))
                .collect(Collectors.toList());
        productSearchRepository.saveAll(docs);
        log.info("[ES] Force reindex complete");
    }

    public void deleteProduct(Product product) {
        productSearchRepository.deleteById(product.getId());
    }

    /**
     * How many hits to ask Elasticsearch for.
     *
     * <p>Not optional. The query carried no size, so Elasticsearch returned its default TEN, and
     * the caller then dropped the ones that were not active or not sold in the shopper's country —
     * after the cut. "macbook" came back empty on a shop with four MacBooks in stock, because ten
     * other documents had scored higher and been filtered away afterwards.
     */
    private static final int MAX_HITS = 100;

    /**
     * Finds products for a shopper's words.
     *
     * <p>Three ways to match, because one is never enough for a laptop shop:
     * <ul>
     *   <li><strong>Prefix</strong> — "mac" must find MacBook, and "thinkp" ThinkPad. Fuzziness
     *       cannot do this: on a three-letter word AUTO allows no edits at all, and "mac" is four
     *       edits away from "macbook".</li>
     *   <li><strong>Fuzzy</strong> — "macbok" and "lattitude" are typos of things we sell.</li>
     *   <li><strong>Category, brand and SKU</strong> — "lenovo", "audio", a code off a label.</li>
     * </ul>
     *
     * <p>Only ACTIVE documents, filtered inside the query rather than after it, so the hits that
     * come back are hits that can be bought.
     */
    public List<ProductDocument> search(String query) {
        String text = query == null ? "" : query.trim();
        if (text.isEmpty()) {
            return Collections.emptyList();
        }
        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(q -> q
                        .bool(b -> b
                                .filter(f -> f.term(t -> t.field("status").value("ACTIVE")))
                                .minimumShouldMatch("1")
                                .should(s -> s
                                        .nested(n -> n
                                                .path("translations")
                                                .query(nq -> nq
                                                        .multiMatch(m -> m
                                                                .fields("translations.title^4")
                                                                .query(text)
                                                                .type(TextQueryType.PhrasePrefix)
                                                        )
                                                )
                                        )
                                )
                                .should(s -> s
                                        .nested(n -> n
                                                .path("translations")
                                                .query(nq -> nq
                                                        .multiMatch(m -> m
                                                                .fields("translations.title^2", "translations.description")
                                                                .query(text)
                                                                .fuzziness("AUTO")
                                                        )
                                                )
                                        )
                                )
                                .should(s -> s
                                        .multiMatch(m -> m
                                                .fields("categoryName", "brandName", "sku")
                                                .query(text)
                                                .fuzziness("AUTO")
                                        )
                                )
                        )
                )
                .withPageable(org.springframework.data.domain.PageRequest.of(0, MAX_HITS))
                .build();

        try {
            SearchHits<ProductDocument> searchHits = elasticsearchOperations.search(nativeQuery, ProductDocument.class);
            return searchHits.getSearchHits().stream()
                    .map(SearchHit::getContent)
                    .collect(Collectors.toList());
        } catch (NoSuchIndexException e) {
            log.warn("[ES] 'products' index not found during search, returning empty results");
            ensureIndexExists();
            return Collections.emptyList();
        }
    }

    private ProductDocument mapToDocument(Product product, List<ProductTranslation> translations) {
        ProductDocument doc = new ProductDocument();
        doc.setId(product.getId());
        doc.setSku(product.getSku());
        doc.setStatus(product.getStatus());
        doc.setCategoryId(product.getCategory().getId());
        
        // Populate category names from all translations to make it searchable
        String categoryNames = categoryTranslationRepository.findAllByCategoryId(product.getCategory().getId())
                .stream().map(t -> t.getName()).collect(Collectors.joining(" "));
        doc.setCategoryName(categoryNames);

        if (product.getBrand() != null) {
            doc.setBrandId(product.getBrand().getId());
            String brandNames = brandTranslationRepository.findAllByBrand_Id(product.getBrand().getId())
                    .stream().map(t -> t.getName()).collect(Collectors.joining(" "));
            doc.setBrandName(brandNames);
        }
        
        doc.setProductType(product.getProductType() != null ? product.getProductType().name() : null);
        doc.setAvailabilityStatus(product.getAvailabilityStatus() != null ? product.getAvailabilityStatus().name() : null);
        doc.setIsSuperDeal(product.getIsSuperDeal());
        doc.setIsLimitedStock(product.getIsLimitedStock());
        doc.setIsRefurbished(product.getIsRefurbished());

        List<ProductDocument.Translation> docTranslations = translations.stream().map(t -> {
            ProductDocument.Translation dt = new ProductDocument.Translation();
            dt.setLanguage(t.getLanguage());
            dt.setTitle(t.getTitle());
            dt.setDescription(t.getDescription());
            dt.setSlug(t.getSlug());
            return dt;
        }).collect(Collectors.toList());
        
        doc.setTranslations(docTranslations);
        return doc;
    }
}
