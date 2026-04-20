package com.buyology.ecommerce.product.search;

import com.buyology.ecommerce.product.repository.ProductRepository;
import com.buyology.ecommerce.product.repository.ProductTranslationRepository;
import com.buyology.ecommerce.product.search.service.ProductSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ElasticsearchReindexRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchReindexRunner.class);

    private final ProductSearchService productSearchService;
    private final ProductRepository productRepository;
    private final ProductTranslationRepository translationRepository;

    public ElasticsearchReindexRunner(
            ProductSearchService productSearchService,
            ProductRepository productRepository,
            ProductTranslationRepository translationRepository) {
        this.productSearchService = productSearchService;
        this.productRepository = productRepository;
        this.translationRepository = translationRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            productSearchService.reindexAll(
                    productRepository.findAll(),
                    product -> translationRepository.findByProductId(product.getId())
            );
        } catch (Exception e) {
            log.error("[ES] Reindex on startup failed — search may return incomplete results", e);
        }
    }
}
