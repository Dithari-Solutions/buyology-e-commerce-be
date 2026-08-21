package com.buyology.ecommerce.assistant.service;

import com.buyology.ecommerce.assistant.dto.AssistantProductCard;
import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.product.dto.CategoryLocalizedResponse;
import com.buyology.ecommerce.product.dto.ProductFilterRequest;
import com.buyology.ecommerce.product.dto.ProductResponse;
import com.buyology.ecommerce.product.service.ProductCategoryService;
import com.buyology.ecommerce.product.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The assistant's window onto the catalog.
 *
 * <p>Every lookup here goes through {@link ProductService}'s <em>public</em> read methods rather
 * than the repositories. That is deliberate and load-bearing: those methods are what decide that a
 * product is ACTIVE, is assigned to a B2C store in the customer's country, is not B2B-quote-only,
 * and what its price is once discounts and currency conversion are applied. Reaching past them
 * would give the assistant a second, subtly different catalog — one that could offer a shopper a
 * product they cannot buy, at a price that is not the price.
 *
 * <p>Retrieval is keyword search over the live catalog, not a vector index. Titles here are short,
 * concrete and full of the exact model names customers type ("MacBook Air M2", "Galaxy S24"), which
 * is the case keyword search is best at; a vector store would add an index to keep in sync with
 * every product edit for little gain at this catalog size.
 */
@Service
public class AssistantCatalogService {

    private static final Logger log = LoggerFactory.getLogger(AssistantCatalogService.class);

    /** Languages ProductCategoryService accepts; anything else is answered in English. */
    private static final List<String> SUPPORTED_LANGUAGES = List.of("EN", "AR", "AZ");

    /**
     * The overview is the same for every visitor in a given market and costs a full catalog scan to
     * build, so it is held briefly rather than rebuilt per message. Short enough that a newly
     * published category shows up within the minute.
     */
    private static final long OVERVIEW_TTL_MS = 60_000L;

    private final ProductService productService;
    private final ProductCategoryService categoryService;

    /** Cached overview per (lang, country, currency) market key. */
    private final Map<String, AtomicReference<CachedOverview>> overviewCache = new java.util.concurrent.ConcurrentHashMap<>();

    public AssistantCatalogService(ProductService productService,
                                   ProductCategoryService categoryService) {
        this.productService = productService;
        this.categoryService = categoryService;
    }

    private record CachedOverview(String text, long builtAtMs) {
    }

    /** Normalises a client-supplied language to one the catalog actually has translations for. */
    public static String normaliseLanguage(String language) {
        if (language == null || language.isBlank()) {
            return "EN";
        }
        String upper = language.trim().toUpperCase(Locale.ROOT);
        return SUPPORTED_LANGUAGES.contains(upper) ? upper : "EN";
    }

    // =========================================================================
    // Catalog overview
    // =========================================================================

    /**
     * A compact description of what the shop sells, included in every prompt.
     *
     * <p>This is what lets "what do you sell?" and "do you have laptops?" be answered at all —
     * questions no keyword search over product titles would ever match. Without it the assistant
     * would have to say it found nothing about the very question customers open a chat to ask.
     */
    @Transactional(readOnly = true)
    public String catalogOverview(String language, String countryCode, String currency) {
        String key = normaliseLanguage(language) + "|" + safe(countryCode) + "|" + safe(currency);
        AtomicReference<CachedOverview> slot = overviewCache.computeIfAbsent(key, k -> new AtomicReference<>());
        CachedOverview cached = slot.get();
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.builtAtMs() < OVERVIEW_TTL_MS) {
            return cached.text();
        }
        String built = buildOverview(language, countryCode, currency);
        slot.set(new CachedOverview(built, now));
        return built;
    }

    private String buildOverview(String language, String countryCode, String currency) {
        StringBuilder sb = new StringBuilder();

        List<CategoryLocalizedResponse> categories = List.of();
        try {
            categories = unwrapList(categoryService.getCategories(normaliseLanguage(language)));
        } catch (Exception e) {
            log.warn("[ASSISTANT] Could not load categories for the catalog overview.", e);
        }
        List<String> names = categories.stream()
                .filter(c -> c.getName() != null && !c.getName().isBlank())
                .filter(c -> c.getStatus() == null || "ACTIVE".equalsIgnoreCase(c.getStatus()))
                .map(CategoryLocalizedResponse::getName)
                .distinct()
                .limit(60)
                .toList();
        if (!names.isEmpty()) {
            sb.append("Product categories currently sold: ").append(String.join(", ", names)).append('\n');
        }

        try {
            BigDecimal[] range = productService.resolveDisplayPriceRange(countryCode, currency, null, null);
            if (range != null && range.length == 2 && range[0] != null && range[1] != null) {
                sb.append("Prices in the catalog run from ").append(range[0].stripTrailingZeros().toPlainString())
                  .append(" to ").append(range[1].stripTrailingZeros().toPlainString());
                if (currency != null && !currency.isBlank()) {
                    sb.append(' ').append(currency.trim().toUpperCase(Locale.ROOT));
                }
                sb.append(".\n");
            }
        } catch (Exception e) {
            log.warn("[ASSISTANT] Could not resolve the catalog price range.", e);
        }

        if (sb.isEmpty()) {
            return "(The catalog overview could not be loaded for this request.)";
        }
        return sb.toString().trim();
    }

    // =========================================================================
    // Retrieval
    // =========================================================================

    /**
     * Products matching the customer's words, scoped to their market.
     *
     * <p>The query is the customer's own text: {@code ProductSpecification} matches it against SKU,
     * translated title and description, which is exactly the surface a shopper's phrasing lands on.
     */
    @Transactional(readOnly = true)
    public List<ProductResponse> search(String query, String language, String countryCode,
                                        String currency, int limit) {
        if (query == null || query.isBlank() || limit <= 0) {
            return List.of();
        }
        ProductFilterRequest filter = new ProductFilterRequest();
        filter.setQ(query.trim());
        try {
            List<ProductResponse> found = unwrapList(productService.searchProducts(
                    filter, normaliseLanguage(language), countryCode, currency, null, null));
            return found.size() <= limit ? found : found.subList(0, limit);
        } catch (Exception e) {
            // A failed lookup degrades the answer; it must not fail the conversation.
            log.warn("[ASSISTANT] Catalog search failed for query '{}'.", query, e);
            return List.of();
        }
    }

    /** Re-loads specific products (the ones cited earlier in the conversation) at current prices. */
    @Transactional(readOnly = true)
    public List<ProductResponse> byIds(List<UUID> ids, String language, String countryCode, String currency) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        try {
            return unwrapList(productService.getProductsByIds(
                    ids, normaliseLanguage(language), countryCode, currency));
        } catch (Exception e) {
            log.warn("[ASSISTANT] Could not re-load {} previously cited product(s).", ids.size(), e);
            return List.of();
        }
    }

    // =========================================================================
    // Rendering for the prompt
    // =========================================================================

    /**
     * Renders products as the plain-text block the model reads.
     *
     * <p>Each entry leads with the product id, because the model's only job with respect to
     * products is to name the ids it used — the customer-facing card is then built from this same
     * {@link ProductResponse}, not from anything the model wrote.
     */
    public String renderForPrompt(List<ProductResponse> products, boolean includeSpecs) {
        if (products == null || products.isEmpty()) {
            return "(no matching products)";
        }
        StringBuilder sb = new StringBuilder();
        for (ProductResponse p : products) {
            sb.append("- id: ").append(p.getId()).append('\n');
            sb.append("  name: ").append(nullSafe(p.getTitle())).append('\n');
            if (p.getBrandName() != null && !p.getBrandName().isBlank()) {
                sb.append("  brand: ").append(p.getBrandName()).append('\n');
            }
            if (p.getStorePrice() != null) {
                sb.append("  price: ").append(p.getStorePrice().stripTrailingZeros().toPlainString());
                if (p.getCurrency() != null) sb.append(' ').append(p.getCurrency());
                if (p.getOriginalPrice() != null) {
                    sb.append(" (was ").append(p.getOriginalPrice().stripTrailingZeros().toPlainString()).append(')');
                }
                sb.append('\n');
            } else {
                sb.append("  price: not available in this market — do not quote a price\n");
            }
            sb.append("  availability: ").append(nullSafe(p.getAvailabilityStatus()));
            if (p.getStockQuantity() != null && p.getStockQuantity() > 0 && p.getStockQuantity() < 5) {
                sb.append(" (only ").append(p.getStockQuantity()).append(" left)");
            }
            sb.append('\n');
            if (Boolean.TRUE.equals(p.getIsRefurbished())) {
                sb.append("  condition: refurbished")
                  .append(p.getRefurbGrade() != null ? " grade " + p.getRefurbGrade() : "")
                  .append('\n');
            }
            if (Boolean.TRUE.equals(p.getIsSuperDeal())) {
                sb.append("  promotion: super deal\n");
            }
            if (p.getAverageRating() != null && p.getTotalReviews() != null && p.getTotalReviews() > 0) {
                sb.append("  rating: ").append(p.getAverageRating()).append("/5 from ")
                  .append(p.getTotalReviews()).append(" reviews\n");
            }
            if (includeSpecs) {
                String specs = renderSpecs(p);
                if (!specs.isEmpty()) {
                    sb.append("  specs: ").append(specs).append('\n');
                }
                String description = trim(p.getDescription(), 600);
                if (description != null) {
                    sb.append("  description: ").append(description.replace('\n', ' ')).append('\n');
                }
            }
        }
        return sb.toString().trim();
    }

    private static String renderSpecs(ProductResponse p) {
        if (p.getSpecs() == null || p.getSpecs().isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (ProductResponse.SpecGroupDto group : p.getSpecs()) {
            if (group.getOptions() == null || group.getOptions().isEmpty()) continue;
            String values = group.getOptions().stream()
                    .map(ProductResponse.SpecOptionDto::getValue)
                    .filter(v -> v != null && !v.isBlank())
                    .reduce((a, b) -> a + "/" + b)
                    .orElse("");
            if (!values.isBlank()) {
                parts.add(nullSafe(group.getName()) + " " + values);
            }
            if (parts.size() >= 12) break;
        }
        return String.join(", ", parts);
    }

    // =========================================================================
    // Cards
    // =========================================================================

    /**
     * Builds the widget's product cards, in the order the model cited them and skipping any id it
     * did not actually receive. That last part matters: it means a hallucinated id renders nothing
     * rather than a broken card, and the model cannot surface a product outside the market scope
     * even if it names one.
     */
    public List<AssistantProductCard> toCards(List<String> citedIds, List<ProductResponse> available, int limit) {
        if (citedIds == null || citedIds.isEmpty() || available == null || available.isEmpty()) {
            return List.of();
        }
        Map<UUID, ProductResponse> byId = new LinkedHashMap<>();
        for (ProductResponse p : available) {
            if (p.getId() != null) byId.put(p.getId(), p);
        }
        List<AssistantProductCard> cards = new ArrayList<>();
        for (String raw : citedIds) {
            if (cards.size() >= limit) break;
            if (raw == null || raw.isBlank()) continue;
            UUID id;
            try {
                id = UUID.fromString(raw.trim());
            } catch (IllegalArgumentException e) {
                continue;
            }
            ProductResponse product = byId.get(id);
            if (product == null) continue;
            if (cards.stream().anyMatch(c -> id.equals(c.getId()))) continue;
            cards.add(toCard(product));
        }
        return cards;
    }

    private static AssistantProductCard toCard(ProductResponse p) {
        AssistantProductCard card = new AssistantProductCard();
        card.setId(p.getId());
        card.setTitle(p.getTitle());
        card.setSlug(p.getSlug());
        card.setBrandName(p.getBrandName());
        card.setAvailabilityStatus(p.getAvailabilityStatus());
        card.setPrice(p.getStorePrice());
        card.setOriginalPrice(p.getOriginalPrice());
        card.setCurrency(p.getCurrency());
        card.setAverageRating(p.getAverageRating());
        card.setIsRefurbished(p.getIsRefurbished());
        card.setImageUrl(primaryImage(p));
        return card;
    }

    private static String primaryImage(ProductResponse p) {
        if (p.getMedia() == null || p.getMedia().isEmpty()) {
            return null;
        }
        return p.getMedia().stream()
                .filter(m -> Boolean.TRUE.equals(m.getIsPrimary()))
                .map(ProductResponse.MediaDto::getUrl)
                .filter(u -> u != null && !u.isBlank())
                .findFirst()
                .orElseGet(() -> p.getMedia().stream()
                        .map(ProductResponse.MediaDto::getUrl)
                        .filter(u -> u != null && !u.isBlank())
                        .findFirst()
                        .orElse(null));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Pulls the payload out of the storefront's {@code ApiResponse} envelope; never returns null. */
    private static <T> List<T> unwrapList(ResponseEntity<ApiResponse<List<T>>> response) {
        ApiResponse<List<T>> body = response == null ? null : response.getBody();
        List<T> data = body == null ? null : body.getData();
        return data == null ? List.of() : data;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String nullSafe(String value) {
        return value == null ? "unknown" : value;
    }

    private static String trim(String value, int max) {
        if (value == null) return null;
        String cleaned = value.trim();
        if (cleaned.isEmpty()) return null;
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max) + "…";
    }
}
