package com.buyology.ecommerce.erpnext.service;

import com.buyology.ecommerce.common.utils.SlugUtils;
import com.buyology.ecommerce.erpnext.config.ErpNextProperties;
import com.buyology.ecommerce.erpnext.dto.ErpImportPreviewRow;
import com.buyology.ecommerce.erpnext.dto.ErpImportResult;
import com.buyology.ecommerce.erpnext.dto.ErpProduct;
import com.buyology.ecommerce.product.domain.Brand;
import com.buyology.ecommerce.product.domain.BrandTranslation;
import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.product.domain.ProductCategory;
import com.buyology.ecommerce.product.domain.ProductCategoryTranslation;
import com.buyology.ecommerce.product.domain.ProductMedia;
import com.buyology.ecommerce.product.domain.ProductTranslation;
import com.buyology.ecommerce.product.repository.BrandRepository;
import com.buyology.ecommerce.product.repository.BrandTranslationRepository;
import com.buyology.ecommerce.product.repository.ProductCategoryRepository;
import com.buyology.ecommerce.product.repository.ProductCategoryTranslationRepository;
import com.buyology.ecommerce.product.repository.ProductMediaRepository;
import com.buyology.ecommerce.product.repository.ProductRepository;
import com.buyology.ecommerce.product.repository.ProductTranslationRepository;
import com.buyology.ecommerce.product.search.service.ProductSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Imports ERPNext {@code Item}s into the ecommerce catalog as base {@link Product}s.
 *
 * <p>ERPNext is the catalog + inventory master here; this pulls its Items into the "general
 * products list" exactly like the dashboard's New Product screen would — <b>without</b>
 * assigning them to any store, so an imported product is not yet for sale. Making it
 * sellable (assigning to a store with a price) stays a deliberate admin step in the
 * dashboard, matching the {@code Product} → {@code StoreProduct} split.
 *
 * <p>Admin-triggered only. Idempotent by SKU ({@code sku == item_code}): re-importing an
 * existing product refreshes its stock/availability instead of duplicating it. Each item is
 * imported in its own transaction, so one bad item never rolls back the rest.
 *
 * <p>Stock is the on-hand quantity summed across ERP warehouses ({@code Bin.actual_qty}).
 * The ERP image is stored as a {@link ProductMedia} URL as-is (not re-hosted); enrichment
 * — translations, media pipeline, variants, per-store pricing — remains a dashboard task.
 */
@Service
public class ErpProductImportService {

    private static final Logger log = LoggerFactory.getLogger(ErpProductImportService.class);

    /** The three storefront languages. Import seeds all three from the ERP (English) name. */
    private static final List<String> LANGS = List.of("EN", "AZ", "AR");
    private static final String DEFAULT_CATEGORY = "Uncategorized";
    private static final int MAX_SLUG_ATTEMPTS = 50;

    private final ErpNextProperties props;
    private final ErpNextClient client;
    private final ProductRepository productRepository;
    private final ProductTranslationRepository translationRepository;
    private final BrandRepository brandRepository;
    private final BrandTranslationRepository brandTranslationRepository;
    private final ProductCategoryRepository categoryRepository;
    private final ProductCategoryTranslationRepository categoryTranslationRepository;
    private final ProductMediaRepository productMediaRepository;
    private final ProductSearchService productSearchService;
    private final TransactionTemplate txTemplate;

    public ErpProductImportService(ErpNextProperties props,
                                   ErpNextClient client,
                                   ProductRepository productRepository,
                                   ProductTranslationRepository translationRepository,
                                   BrandRepository brandRepository,
                                   BrandTranslationRepository brandTranslationRepository,
                                   ProductCategoryRepository categoryRepository,
                                   ProductCategoryTranslationRepository categoryTranslationRepository,
                                   ProductMediaRepository productMediaRepository,
                                   ProductSearchService productSearchService,
                                   PlatformTransactionManager transactionManager) {
        this.props = props;
        this.client = client;
        this.productRepository = productRepository;
        this.translationRepository = translationRepository;
        this.brandRepository = brandRepository;
        this.brandTranslationRepository = brandTranslationRepository;
        this.categoryRepository = categoryRepository;
        this.categoryTranslationRepository = categoryTranslationRepository;
        this.productMediaRepository = productMediaRepository;
        this.productSearchService = productSearchService;
        this.txTemplate = new TransactionTemplate(transactionManager);
    }

    public boolean enabled() {
        return props.isEnabled();
    }

    // ── preview (read-only) ───────────────────────────────────────────────────

    /** A page of ERP items with their warehouse stock and whether each is already imported. */
    public List<ErpImportPreviewRow> preview(int limit, int offset) {
        List<ErpProduct> items = client.listProducts(limit, offset);
        List<String> codes = items.stream()
                .map(this::codeOf).filter(Objects::nonNull).toList();
        Map<String, Double> stock = codes.isEmpty() ? Map.of() : client.stockByItemCode(codes);

        List<ErpImportPreviewRow> rows = new ArrayList<>();
        for (ErpProduct p : items) {
            String code = codeOf(p);
            double qty = code == null ? 0d : stock.getOrDefault(code, 0d);
            boolean exists = code != null && productRepository.existsBySku(code);
            rows.add(new ErpImportPreviewRow(
                    code, p.itemName(), p.itemGroup(), p.brand(), p.standardRate(), p.image(), qty, exists));
        }
        return rows;
    }

    // ── import ────────────────────────────────────────────────────────────────

    /**
     * Import (create-or-update) the given ERP item codes. Each item runs in its own
     * transaction; failures are reported per item, never aborting the batch.
     */
    public List<ErpImportResult> importByCodes(List<String> requestedCodes) {
        List<String> codes = requestedCodes.stream()
                .filter(c -> c != null && !c.isBlank()).map(String::trim).distinct().toList();
        if (codes.isEmpty()) return List.of();

        List<ErpProduct> items = client.getItemsByCode(codes);
        Map<String, Double> stock = client.stockByItemCode(codes);

        List<ErpImportResult> results = new ArrayList<>();
        Set<String> found = new LinkedHashSet<>();
        for (ErpProduct item : items) {
            String code = codeOf(item);
            if (code == null) continue;
            found.add(code);
            try {
                results.add(txTemplate.execute(status -> importOne(item, stock.getOrDefault(code, 0d))));
            } catch (Exception e) {
                log.error("[ERPNEXT] import failed for item {}: {}", code, e.getMessage());
                results.add(ErpImportResult.failed(code, e.getMessage()));
            }
        }
        // Requested codes ERPNext did not return (typo / disabled / deleted there).
        for (String code : codes) {
            if (!found.contains(code)) {
                results.add(ErpImportResult.failed(code, "Not found in ERPNext"));
            }
        }
        return results;
    }

    /** Create or update one product from an ERP item. Runs inside a transaction. */
    private ErpImportResult importOne(ErpProduct item, double stock) {
        String sku = codeOf(item);
        if (sku == null) return ErpImportResult.failed(item.name(), "ERP item has no item_code");

        int qty = (int) Math.round(stock);
        Product.AvailabilityStatus availability = qty > 0
                ? Product.AvailabilityStatus.IN_STOCK
                : Product.AvailabilityStatus.OUT_OF_STOCK;

        // Idempotent: refresh stock/availability on an already-imported product, don't clobber
        // admin edits (titles, media, store assignments, pricing).
        Product existing = productRepository.findBySku(sku).orElse(null);
        if (existing != null) {
            existing.setStockQuantity(qty);
            existing.setAvailabilityStatus(availability);
            productRepository.save(existing);
            return ErpImportResult.updated(sku, existing.getId().toString(),
                    "Refreshed stock=" + qty + " / " + availability);
        }

        ProductCategory category = resolveCategory(item.itemGroup());
        Brand brand = (item.brand() != null && !item.brand().isBlank())
                ? resolveBrand(item.brand().trim())
                : null;

        Product product = new Product(
                category, brand,
                Product.ProductType.SIMPLE,
                false, null,
                sku,
                "ACTIVE",
                availability,
                false, false);
        product.setStockQuantity(qty);
        Product saved = productRepository.save(product);

        String title = item.itemName() != null && !item.itemName().isBlank() ? item.itemName().trim() : sku;
        String description = stripHtml(item.description());
        List<ProductTranslation> translations = new ArrayList<>();
        for (String lang : LANGS) {
            String slug = uniqueProductSlug(title, sku, lang);
            translations.add(translationRepository.save(
                    new ProductTranslation(saved, lang, title, description, slug)));
        }

        if (item.image() != null && !item.image().isBlank()) {
            ProductMedia media = new ProductMedia();
            media.setProduct(saved);
            media.setMediaType(ProductMedia.MediaType.IMAGE);
            media.setUrl(item.image());
            media.setIsPrimary(true);
            media.setOrderIndex(0);
            productMediaRepository.save(media);
        }

        // Keep search in sync like ProductService.createProduct does; never fail the import on it.
        try {
            productSearchService.indexProduct(saved, translations);
        } catch (Exception e) {
            log.warn("[ERPNEXT] product {} imported but ES indexing failed: {}", sku, e.getMessage());
        }

        return ErpImportResult.created(sku, saved.getId().toString());
    }

    // ── category / brand resolution (find-or-create by name) ───────────────────

    private ProductCategory resolveCategory(String itemGroup) {
        String name = (itemGroup == null || itemGroup.isBlank()) ? DEFAULT_CATEGORY : itemGroup.trim();
        return categoryTranslationRepository
                .findFirstByLanguageIgnoreCaseAndNameIgnoreCase("EN", name)
                .map(ProductCategoryTranslation::getCategory)
                .orElseGet(() -> createCategory(name));
    }

    private ProductCategory createCategory(String name) {
        ProductCategory category = categoryRepository.save(new ProductCategory(null, "ACTIVE"));
        for (String lang : LANGS) {
            ProductCategoryTranslation t = new ProductCategoryTranslation();
            t.setCategory(category);
            t.setLanguage(lang);
            t.setName(name);
            t.setSlug(uniqueCategorySlug(name, lang));
            categoryTranslationRepository.save(t);
        }
        log.info("[ERPNEXT] created category \"{}\" from ERP item_group", name);
        return category;
    }

    private Brand resolveBrand(String name) {
        return brandTranslationRepository
                .findFirstByLanguageIgnoreCaseAndNameIgnoreCase("EN", name)
                .map(BrandTranslation::getBrand)
                .orElseGet(() -> createBrand(name));
    }

    private Brand createBrand(String name) {
        Brand brand = new Brand();
        brand.setStatus("ACTIVE");
        Brand saved = brandRepository.save(brand);
        for (String lang : LANGS) {
            brandTranslationRepository.save(new BrandTranslation(saved, lang, name));
        }
        log.info("[ERPNEXT] created brand \"{}\" from ERP brand", name);
        return saved;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private String codeOf(ErpProduct p) {
        if (p == null) return null;
        if (p.itemCode() != null && !p.itemCode().isBlank()) return p.itemCode().trim();
        return p.name() != null && !p.name().isBlank() ? p.name().trim() : null;
    }

    /** Product slug unique within a language: base → base-<sku> → base-<sku>-N. */
    private String uniqueProductSlug(String title, String sku, String lang) {
        String base = firstNonBlank(SlugUtils.toSlug(title), SlugUtils.toSlug(sku), "item");
        if (!translationRepository.existsActiveByLanguageAndSlug(lang, base)) return base;
        String withSku = base + "-" + firstNonBlank(SlugUtils.toSlug(sku), "x");
        if (!translationRepository.existsActiveByLanguageAndSlug(lang, withSku)) return withSku;
        for (int i = 2; i < MAX_SLUG_ATTEMPTS; i++) {
            String candidate = withSku + "-" + i;
            if (!translationRepository.existsActiveByLanguageAndSlug(lang, candidate)) return candidate;
        }
        return withSku + "-" + System.identityHashCode(this); // extremely unlikely fallback
    }

    /** Category slug unique within a language. */
    private String uniqueCategorySlug(String name, String lang) {
        String base = firstNonBlank(SlugUtils.toSlug(name), "category");
        if (!categoryTranslationRepository.existsBySlugAndLanguage(base, lang)) return base;
        for (int i = 2; i < MAX_SLUG_ATTEMPTS; i++) {
            String candidate = base + "-" + i;
            if (!categoryTranslationRepository.existsBySlugAndLanguage(candidate, lang)) return candidate;
        }
        return base + "-x";
    }

    /** Strip HTML tags/entities from ERP rich-text so we store plain description copy. */
    private static String stripHtml(String html) {
        if (html == null || html.isBlank()) return null;
        String text = html.replaceAll("<[^>]*>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replaceAll("\\s+", " ")
                .trim();
        return text.isBlank() ? null : text;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return "";
    }
}
