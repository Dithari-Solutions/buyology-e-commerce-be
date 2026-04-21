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

    public void indexProduct(Product product, List<ProductTranslation> translations) {
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

    public List<ProductDocument> search(String query) {
        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(q -> q
                        .bool(b -> b
                                .should(s -> s
                                        .nested(n -> n
                                                .path("translations")
                                                .query(nq -> nq
                                                        .multiMatch(m -> m
                                                                .fields("translations.title^2", "translations.description")
                                                                .query(query)
                                                                .fuzziness("AUTO")
                                                        )
                                                )
                                        )
                                )
                                .should(s -> s
                                        .multiMatch(m -> m
                                                .fields("categoryName", "brandName", "sku")
                                                .query(query)
                                                .fuzziness("AUTO")
                                        )
                                )
                        )
                )
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
