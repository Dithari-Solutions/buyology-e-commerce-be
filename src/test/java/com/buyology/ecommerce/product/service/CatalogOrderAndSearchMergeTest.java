package com.buyology.ecommerce.product.service;

import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.product.domain.ProductCategory;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins what a shopper sees first, and what a search is allowed to miss.
 *
 * <p>Two separate complaints from the shop: "All products" opened on accessories while the shop
 * sells laptops, and the search box found none of the four MacBooks in stock.
 */
class CatalogOrderAndSearchMergeTest {

    private static final UUID LAPTOPS = UUID.randomUUID();
    private static final UUID GAMING_LAPTOPS = UUID.randomUUID();   // a child of Laptops
    private static final UUID AUDIO = UUID.randomUUID();

    private static Product product(String sku, UUID categoryId) {
        Product p = new Product();
        p.setId(UUID.randomUUID());
        p.setSku(sku);
        if (categoryId != null) {
            ProductCategory category = new ProductCategory();
            ReflectionTestUtils.setField(category, "id", categoryId);
            p.setCategory(category);
        }
        return p;
    }

    private static List<String> skus(List<Product> products) {
        return products.stream().map(Product::getSku).toList();
    }

    // ── Laptops first ────────────────────────────────────────────────────────

    @Test
    void laptopsComeFirstAndEverythingElseKeepsItsOrder() {
        List<Product> catalogue = List.of(
                product("earbuds", AUDIO),
                product("macbook", LAPTOPS),
                product("speaker", AUDIO),
                product("thinkpad", LAPTOPS),
                product("hub", AUDIO));

        List<Product> ordered = ProductService.leadWithCategories(catalogue, Set.of(LAPTOPS));

        assertEquals(List.of("macbook", "thinkpad", "earbuds", "speaker", "hub"), skus(ordered));
    }

    @Test
    void aSubCategoryOfLaptopsLeadsToo() {
        List<Product> catalogue = List.of(
                product("earbuds", AUDIO),
                product("gaming-laptop", GAMING_LAPTOPS));

        List<Product> ordered = ProductService.leadWithCategories(catalogue, Set.of(LAPTOPS, GAMING_LAPTOPS));

        assertEquals(List.of("gaming-laptop", "earbuds"), skus(ordered));
    }

    @Test
    void nothingIsDroppedOrReorderedWhenTheCategoryCannotBeResolved() {
        List<Product> catalogue = List.of(product("earbuds", AUDIO), product("macbook", LAPTOPS));

        assertEquals(skus(catalogue), skus(ProductService.leadWithCategories(catalogue, Set.of())));
        assertEquals(skus(catalogue), skus(ProductService.leadWithCategories(catalogue, null)));
    }

    @Test
    void aProductWithNoCategoryIsKeptAndSortsAfterTheLeaders() {
        List<Product> catalogue = List.of(product("orphan", null), product("macbook", LAPTOPS));

        assertEquals(List.of("macbook", "orphan"),
                skus(ProductService.leadWithCategories(catalogue, Set.of(LAPTOPS))));
    }

    // ── Search recall ────────────────────────────────────────────────────────

    @Test
    void theIndexRanksAndTheDatabaseFillsInWhatItMissed() {
        // The index had two of the MacBooks; the database knows about a third that was added or
        // renamed since it was last indexed. Ranking order is the index's, and nothing is lost.
        UUID indexed1 = UUID.randomUUID();
        UUID indexed2 = UUID.randomUUID();
        UUID onlyInDb = UUID.randomUUID();

        var merged = ProductService.mergeKeepingOrder(
                List.of(indexed1, indexed2), List.of(indexed2, onlyInDb));

        assertEquals(List.of(indexed1, indexed2, onlyInDb), List.copyOf(merged));
    }

    @Test
    void anEmptyIndexResultStillReturnsWhatTheDatabaseFound() {
        UUID found = UUID.randomUUID();

        assertEquals(List.of(found), List.copyOf(ProductService.mergeKeepingOrder(List.of(), List.of(found))));
    }
}
