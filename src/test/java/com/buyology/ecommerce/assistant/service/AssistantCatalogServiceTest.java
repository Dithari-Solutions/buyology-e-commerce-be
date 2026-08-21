package com.buyology.ecommerce.assistant.service;

import com.buyology.ecommerce.assistant.dto.AssistantProductCard;
import com.buyology.ecommerce.product.dto.ProductResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the guard between what the model says and what the customer is shown.
 *
 * <p>The model returns product ids, and cards are built by looking those ids up in the rows the
 * server actually retrieved — never from anything the model wrote. That is what makes an invented
 * id harmless: it matches nothing and renders nothing, instead of putting a product that does not
 * exist, or one that is not sold in this market, in front of a shopper with a price beside it.
 *
 * <p>The rendering assertions cover the other half: a product with no resolvable price in the
 * customer's market must reach the model as an explicit instruction not to quote one, because the
 * plausible thing for a model to do with a missing price is estimate it.
 */
class AssistantCatalogServiceTest {

    /** Only pure rendering/mapping is exercised, so the collaborators can be null. */
    private final AssistantCatalogService catalog = new AssistantCatalogService(null, null);

    private static ProductResponse product(UUID id, String title, BigDecimal price) {
        ProductResponse p = new ProductResponse();
        p.setId(id);
        p.setTitle(title);
        p.setSlug(title.toLowerCase().replace(' ', '-'));
        p.setAvailabilityStatus("IN_STOCK");
        if (price != null) {
            p.setStorePrice(price);
            p.setCurrency("AED");
        }
        return p;
    }

    // ── Card building ────────────────────────────────────────────────────────

    @Test
    void buildsCardsInTheOrderTheModelCitedThem() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        List<ProductResponse> retrieved = List.of(
                product(first, "MacBook Air M2", new BigDecimal("3999.00")),
                product(second, "Galaxy S24", new BigDecimal("2799.00")));

        List<AssistantProductCard> cards =
                catalog.toCards(List.of(second.toString(), first.toString()), retrieved, 4);

        assertEquals(List.of(second, first), cards.stream().map(AssistantProductCard::getId).toList());
        assertEquals("Galaxy S24", cards.get(0).getTitle());
        assertEquals(new BigDecimal("2799.00"), cards.get(0).getPrice());
        assertEquals("AED", cards.get(0).getCurrency());
    }

    @Test
    void ignoresAnIdThatWasNotRetrieved() {
        UUID real = UUID.randomUUID();
        List<ProductResponse> retrieved = List.of(product(real, "MacBook Air M2", new BigDecimal("3999.00")));

        // A hallucinated id, and an id for a real product that is not sold in this market — both
        // reach the customer as nothing at all rather than as a card.
        List<AssistantProductCard> cards = catalog.toCards(
                List.of(UUID.randomUUID().toString(), real.toString(), UUID.randomUUID().toString()),
                retrieved, 4);

        assertEquals(1, cards.size());
        assertEquals(real, cards.get(0).getId());
    }

    @Test
    void ignoresMalformedIdsWithoutFailingTheTurn() {
        UUID real = UUID.randomUUID();
        List<ProductResponse> retrieved = List.of(product(real, "MacBook Air M2", null));

        List<AssistantProductCard> cards =
                catalog.toCards(java.util.Arrays.asList("not-a-uuid", "", null, real.toString()),
                        retrieved, 4);

        assertEquals(1, cards.size());
        assertEquals(real, cards.get(0).getId());
    }

    @Test
    void deduplicatesAndRespectsTheCardLimit() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        List<ProductResponse> retrieved = List.of(
                product(a, "One", BigDecimal.ONE),
                product(b, "Two", BigDecimal.ONE),
                product(c, "Three", BigDecimal.ONE));

        List<AssistantProductCard> deduped =
                catalog.toCards(List.of(a.toString(), a.toString(), b.toString()), retrieved, 4);
        assertEquals(List.of(a, b), deduped.stream().map(AssistantProductCard::getId).toList());

        List<AssistantProductCard> capped =
                catalog.toCards(List.of(a.toString(), b.toString(), c.toString()), retrieved, 2);
        assertEquals(2, capped.size());
    }

    @Test
    void returnsNoCardsWhenTheModelCitedNothing() {
        List<ProductResponse> retrieved = List.of(product(UUID.randomUUID(), "One", BigDecimal.ONE));
        assertTrue(catalog.toCards(List.of(), retrieved, 4).isEmpty());
        assertTrue(catalog.toCards(null, retrieved, 4).isEmpty());
    }

    // ── Prompt rendering ─────────────────────────────────────────────────────

    @Test
    void rendersPriceAndIdForEachProduct() {
        UUID id = UUID.randomUUID();
        String rendered = catalog.renderForPrompt(
                List.of(product(id, "MacBook Air M2", new BigDecimal("3999.00"))), false);

        assertTrue(rendered.contains(id.toString()), "the id is how the model refers back to a product");
        assertTrue(rendered.contains("MacBook Air M2"));
        assertTrue(rendered.contains("3999"));
        assertTrue(rendered.contains("AED"));
    }

    @Test
    void tellsTheModelNotToQuoteAPriceItDoesNotHave() {
        String rendered = catalog.renderForPrompt(
                List.of(product(UUID.randomUUID(), "MacBook Air M2", null)), false);

        assertTrue(rendered.contains("do not quote a price"),
                "a missing price must be an explicit instruction, not a blank the model fills in");
    }

    @Test
    void rendersAPlaceholderWhenNothingMatched() {
        assertEquals("(no matching products)", catalog.renderForPrompt(List.of(), true));
        assertEquals("(no matching products)", catalog.renderForPrompt(null, true));
    }

    // ── Language normalisation ───────────────────────────────────────────────

    @Test
    void normalisesLanguageToOneTheCatalogIsTranslatedInto() {
        assertEquals("EN", AssistantCatalogService.normaliseLanguage("en"));
        assertEquals("AR", AssistantCatalogService.normaliseLanguage(" ar "));
        assertEquals("AZ", AssistantCatalogService.normaliseLanguage("AZ"));
        // ProductCategoryService throws on an unknown language, so an unsupported or absent value
        // has to become English here rather than fail the customer's question.
        assertEquals("EN", AssistantCatalogService.normaliseLanguage("fr"));
        assertEquals("EN", AssistantCatalogService.normaliseLanguage(null));
        assertEquals("EN", AssistantCatalogService.normaliseLanguage("  "));
    }
}
