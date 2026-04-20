package com.buyology.ecommerce.product.search.service;

import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.product.domain.ProductTranslation;
import com.buyology.ecommerce.product.search.domain.ProductDocument;
import com.buyology.ecommerce.product.search.repository.ProductSearchRepository;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductSearchService {

    private final ProductSearchRepository productSearchRepository;
    private final ElasticsearchOperations elasticsearchOperations;

    public ProductSearchService(ProductSearchRepository productSearchRepository, ElasticsearchOperations elasticsearchOperations) {
        this.productSearchRepository = productSearchRepository;
        this.elasticsearchOperations = elasticsearchOperations;
    }

    public void indexProduct(Product product, List<ProductTranslation> translations) {
        ProductDocument doc = mapToDocument(product, translations);
        productSearchRepository.save(doc);
    }

    public void deleteProduct(Product product) {
        productSearchRepository.deleteById(product.getId());
    }

    public List<ProductDocument> search(String query) {
        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(q -> q
                        .multiMatch(m -> m
                                .fields("translations.title", "translations.description", "categoryName", "brandName")
                                .query(query)
                        )
                )
                .build();

        SearchHits<ProductDocument> searchHits = elasticsearchOperations.search(nativeQuery, ProductDocument.class);
        return searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList());
    }

    private ProductDocument mapToDocument(Product product, List<ProductTranslation> translations) {
        ProductDocument doc = new ProductDocument();
        doc.setId(product.getId());
        doc.setSku(product.getSku());
        doc.setStatus(product.getStatus());
        doc.setCategoryId(product.getCategory().getId());
        // Category name and Brand name would ideally come from their translations too,
        // but for simplicity we take them from the entity if available or just leave null for now
        // In a real app, you'd fetch the primary language translation.
        
        doc.setBrandId(product.getBrand() != null ? product.getBrand().getId() : null);
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
