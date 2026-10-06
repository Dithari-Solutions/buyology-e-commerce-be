package com.buyology.ecommerce.store.service;

import com.buyology.ecommerce.common.utils.BusinessZone;
import com.buyology.ecommerce.product.domain.Brand;
import com.buyology.ecommerce.product.domain.BrandTranslation;
import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.product.domain.ProductCategory;
import com.buyology.ecommerce.product.domain.ProductCategoryTranslation;
import com.buyology.ecommerce.product.domain.ProductMedia;
import com.buyology.ecommerce.product.domain.ProductTranslation;
import com.buyology.ecommerce.product.repository.BrandTranslationRepository;
import com.buyology.ecommerce.product.repository.ProductCategoryTranslationRepository;
import com.buyology.ecommerce.product.repository.ProductMediaRepository;
import com.buyology.ecommerce.product.repository.ProductTranslationRepository;
import com.buyology.ecommerce.store.domain.Store;
import com.buyology.ecommerce.store.domain.StoreProduct;
import com.buyology.ecommerce.store.domain.StoreProductVariant;
import com.buyology.ecommerce.store.dto.StoreProductExport;
import com.buyology.ecommerce.store.enums.StoreProductExportFormat;
import com.buyology.ecommerce.store.repository.StoreProductRepository;
import com.buyology.ecommerce.store.repository.StoreProductVariantRepository;
import com.buyology.ecommerce.store.repository.StoreRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A store's products as a file — Excel for working with, PDF for reading or sending.
 *
 * <p>Covers every listing the store-products page shows (inactive ones included, marked as such),
 * in English, sorted by name.
 */
@Service
public class StoreProductExportService {

    private static final String LANGUAGE = "EN";
    // The storefront serves a product at /{lang}/shop/{slug}; the English path is the canonical one.
    private static final String PRODUCT_PATH = "/en/shop/";
    private static final String MEDIA_PATH = "/api/product/media/";
    // Sale dates read as the calendar day an admin picked — sale end dates are set in Dubai time.
    private static final DateTimeFormatter SALE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(BusinessZone.ID);

    private final StoreRepository storeRepository;
    private final StoreProductRepository storeProductRepository;
    private final StoreProductVariantRepository storeProductVariantRepository;
    private final ProductTranslationRepository translationRepository;
    private final ProductMediaRepository mediaRepository;
    private final BrandTranslationRepository brandTranslationRepository;
    private final ProductCategoryTranslationRepository categoryTranslationRepository;
    private final String webBaseUrl;
    private final String apiBaseUrl;

    public StoreProductExportService(
            StoreRepository storeRepository,
            StoreProductRepository storeProductRepository,
            StoreProductVariantRepository storeProductVariantRepository,
            ProductTranslationRepository translationRepository,
            ProductMediaRepository mediaRepository,
            BrandTranslationRepository brandTranslationRepository,
            ProductCategoryTranslationRepository categoryTranslationRepository,
            @Value("${app.web-base-url:https://buyology.online}") String webBaseUrl,
            @Value("${app.api-base-url:https://api.buyology.online}") String apiBaseUrl) {
        this.storeRepository = storeRepository;
        this.storeProductRepository = storeProductRepository;
        this.storeProductVariantRepository = storeProductVariantRepository;
        this.translationRepository = translationRepository;
        this.mediaRepository = mediaRepository;
        this.brandTranslationRepository = brandTranslationRepository;
        this.categoryTranslationRepository = categoryTranslationRepository;
        this.webBaseUrl = withoutTrailingSlash(webBaseUrl);
        this.apiBaseUrl = withoutTrailingSlash(apiBaseUrl);
    }

    public record ExportFile(byte[] content, String fileName, String contentType) {
    }

    @Transactional(readOnly = true)
    public ExportFile export(UUID storeId, StoreProductExportFormat format) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new IllegalArgumentException("Store not found: " + storeId));
        StoreProductExport export = collect(store);
        String baseName = fileSlug(store) + "-products-" + LocalDate.now(ZoneOffset.UTC);

        return switch (format) {
            case XLSX -> new ExportFile(StoreProductCatalogWorkbook.write(export),
                    baseName + ".xlsx", StoreProductCatalogWorkbook.CONTENT_TYPE);
            case PDF -> new ExportFile(StoreProductCatalogPdf.write(export),
                    baseName + ".pdf", StoreProductCatalogPdf.CONTENT_TYPE);
        };
    }

    private StoreProductExport collect(Store store) {
        // One instant for the whole file, so two rows cannot straddle the moment a sale ends.
        Instant now = Instant.now();
        String currency = store.getCountry() != null ? store.getCountry().getCurrency() : null;
        List<StoreProduct> listings = storeProductRepository.findForExportByStoreId(store.getId());
        if (listings.isEmpty()) {
            return new StoreProductExport(store.getName(), currency, now, List.of());
        }

        List<UUID> productIds = listings.stream().map(sp -> sp.getProduct().getId()).distinct().toList();
        List<UUID> listingIds = listings.stream().map(StoreProduct::getId).toList();

        Map<UUID, ProductTranslation> translations = translationRepository.findByProductIdIn(productIds).stream()
                .collect(Collectors.groupingBy(t -> t.getProduct().getId(),
                        Collectors.collectingAndThen(Collectors.toList(), StoreProductExportService::preferEnglish)));
        Map<UUID, List<ProductMedia>> mediaByProduct = mediaRepository.findByProductIdIn(productIds).stream()
                .collect(Collectors.groupingBy(m -> m.getProduct().getId()));
        Map<UUID, List<StoreProductVariant>> variantsByListing = storeProductVariantRepository
                .findWithVariantByStoreProductIds(listingIds).stream()
                .collect(Collectors.groupingBy(v -> v.getStoreProduct().getId()));
        Map<UUID, String> brandNames = new HashMap<>();
        Map<UUID, String> categoryNames = new HashMap<>();

        List<StoreProductExport.Row> rows = new ArrayList<>();
        for (StoreProduct sp : listings) {
            Product product = sp.getProduct();
            ProductTranslation translation = translations.get(product.getId());
            List<StoreProductVariant> variants = variantsByListing.getOrDefault(sp.getId(), List.of());

            rows.add(new StoreProductExport.Row(
                    translation != null && notBlank(translation.getTitle()) ? translation.getTitle() : product.getSku(),
                    product.getSku(),
                    brandName(product.getBrand(), brandNames),
                    categoryName(product.getCategory(), categoryNames),
                    translation != null && translation.getDescription() != null ? translation.getDescription().trim() : "",
                    sp.getStorePrice(),
                    sp.hasDiscount(now) ? sp.effectivePrice(now) : null,
                    discountLabel(sp, now),
                    availabilityLabel(product.getAvailabilityStatus()),
                    availableQuantity(product.getAvailableQuantity(), variants),
                    conditionLabel(product),
                    Boolean.TRUE.equals(sp.getIsActive()),
                    channelsLabel(sp),
                    translation != null && notBlank(translation.getSlug())
                            ? webBaseUrl + PRODUCT_PATH + UriUtils.encodePathSegment(translation.getSlug(), StandardCharsets.UTF_8)
                            : "",
                    images(mediaByProduct.getOrDefault(product.getId(), List.of())),
                    variants.stream()
                            .sorted(Comparator.comparing(v -> v.getVariant().getSku(), Comparator.nullsLast(String::compareTo)))
                            .map(v -> new StoreProductExport.Variant(v.getVariant().getSku(), v.getStorePrice(),
                                    v.getStock(), Boolean.TRUE.equals(v.getIsActive())))
                            .toList(),
                    sp.getUpdatedAt()));
        }
        rows.sort(Comparator.comparing(StoreProductExport.Row::name, String.CASE_INSENSITIVE_ORDER));
        return new StoreProductExport(store.getName(), currency, now, rows);
    }

    /**
     * Units this store can still sell of a product, or null when nothing limits it.
     *
     * <p>Two counts can refuse an order, and the smaller one is what is actually available: the
     * store's own stock of each active variant, and the product's {@code availableQuantity} (null =
     * not tracked). {@code stockQuantity} is deliberately not used — it is a display hint, not an
     * inventory count (see {@link Product}).
     */
    static Integer availableQuantity(Integer productUnits, List<StoreProductVariant> variants) {
        List<StoreProductVariant> active = variants.stream()
                .filter(v -> Boolean.TRUE.equals(v.getIsActive()))
                .toList();
        Integer storeUnits = active.isEmpty() ? null : active.stream()
                .mapToInt(v -> v.getStock() == null ? 0 : Math.max(0, v.getStock()))
                .sum();
        Integer productLimit = productUnits == null ? null : Math.max(0, productUnits);

        if (storeUnits == null) return productLimit;
        if (productLimit == null) return storeUnits;
        return Math.min(storeUnits, productLimit);
    }

    /**
     * The product's images as permanent links, thumbnail first.
     *
     * <p>The thumbnail is the image the storefront leads with: the primary product-level image, else
     * any primary one, else the first in gallery order. Videos are left out.
     */
    List<StoreProductExport.Image> images(List<ProductMedia> media) {
        List<ProductMedia> ordered = media.stream()
                .filter(m -> m.getMediaType() == ProductMedia.MediaType.IMAGE)
                .sorted(Comparator
                        .comparing((ProductMedia m) -> m.getColorOption() != null)
                        .thenComparing(m -> m.getOrderIndex() == null ? Integer.MAX_VALUE : m.getOrderIndex()))
                .toList();
        if (ordered.isEmpty()) return List.of();

        ProductMedia thumbnail = ordered.stream()
                .filter(m -> Boolean.TRUE.equals(m.getIsPrimary()) && m.getColorOption() == null)
                .findFirst()
                .or(() -> ordered.stream().filter(m -> Boolean.TRUE.equals(m.getIsPrimary())).findFirst())
                .orElse(ordered.get(0));

        List<StoreProductExport.Image> images = new ArrayList<>(ordered.size());
        images.add(new StoreProductExport.Image(mediaUrl(thumbnail), true));
        for (ProductMedia m : ordered) {
            if (m != thumbnail) images.add(new StoreProductExport.Image(mediaUrl(m), false));
        }
        return images;
    }

    private String mediaUrl(ProductMedia media) {
        return apiBaseUrl + MEDIA_PATH + media.getId();
    }

    private String brandName(Brand brand, Map<UUID, String> cache) {
        if (brand == null) return "";
        return cache.computeIfAbsent(brand.getId(), id -> englishOrFirst(
                brandTranslationRepository.findAllByBrand_Id(id),
                BrandTranslation::getLanguage, BrandTranslation::getName));
    }

    private String categoryName(ProductCategory category, Map<UUID, String> cache) {
        if (category == null) return "";
        return cache.computeIfAbsent(category.getId(), id -> englishOrFirst(
                categoryTranslationRepository.findAllByCategoryId(id),
                ProductCategoryTranslation::getLanguage, ProductCategoryTranslation::getName));
    }

    private static ProductTranslation preferEnglish(List<ProductTranslation> translations) {
        return translations.stream()
                .filter(t -> LANGUAGE.equalsIgnoreCase(t.getLanguage()))
                .findFirst()
                .orElse(translations.get(0));
    }

    private static <T> String englishOrFirst(List<T> translations, Function<T, String> language, Function<T, String> name) {
        return translations.stream()
                .filter(t -> LANGUAGE.equalsIgnoreCase(language.apply(t)))
                .findFirst()
                .or(() -> translations.stream().findFirst())
                .map(name)
                .orElse("");
    }

    /**
     * The discount and, when it has one, its window — "10% off until 2026-10-12". A discount outside
     * its window still gets a label, saying it is scheduled or has ended, so the file explains why
     * that row has no sale price.
     */
    static String discountLabel(StoreProduct sp, Instant now) {
        if (sp.getDiscountType() == null || sp.getDiscountValue() == null) return "";
        String discount = switch (sp.getDiscountType()) {
            case PERCENTAGE -> sp.getDiscountValue().stripTrailingZeros().toPlainString() + "% off";
            case FIXED -> "Fixed sale price";
        };
        Instant starts = sp.getDiscountStartsAt();
        Instant ends = sp.getDiscountEndsAt();
        if (starts != null && now.isBefore(starts)) return discount + " (scheduled from " + SALE_DATE.format(starts) + ")";
        if (ends != null && ends.isBefore(now)) return discount + " (ended " + SALE_DATE.format(ends) + ")";
        if (ends != null) return discount + " until " + SALE_DATE.format(ends);
        return discount;
    }

    static String availabilityLabel(Product.AvailabilityStatus status) {
        if (status == null) return "";
        return switch (status) {
            case IN_STOCK -> "In stock";
            case OUT_OF_STOCK -> "Out of stock";
            case PRE_ORDER -> "Pre-order";
        };
    }

    private static String conditionLabel(Product product) {
        if (!Boolean.TRUE.equals(product.getIsRefurbished())) return "New";
        return product.getRefurbGrade() == null ? "Refurbished" : "Refurbished (Grade " + product.getRefurbGrade() + ")";
    }

    private static String channelsLabel(StoreProduct sp) {
        boolean b2c = Boolean.TRUE.equals(sp.getB2cEnabled());
        boolean b2b = Boolean.TRUE.equals(sp.getB2bEnabled());
        if (b2c && b2b) return "B2C, B2B";
        if (b2c) return "B2C";
        if (b2b) return "B2B";
        return "None";
    }

    private static String fileSlug(Store store) {
        String source = notBlank(store.getSlug()) ? store.getSlug() : store.getName();
        String slug = source == null ? "" : source.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug.isEmpty() ? "store" : slug;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String withoutTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
