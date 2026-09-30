package com.buyology.ecommerce.infrastructure.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins how the catalogue cache behaves during a flash sale.
 *
 * <p>Two failures to keep out, pulling in opposite directions. Serving a sale price after the sale has
 * ended is a price the shop is not offering; and switching the cache off whenever a sale is running —
 * which the first attempt did, by refusing to store any body that mentioned one — pays the whole cost
 * of the guard on the hottest read path in the shop and gets a fraction of its benefit, in the week
 * that path is under the most load.
 *
 * <p>The answer is an entry that expires at the SALE'S own end rather than at the flat TTL, so both
 * are false at once.
 */
class CatalogueCacheFlashSaleTest {

    private static final String PATH = "/api/product/search";

    private final CatalogueCacheFilter filter = new CatalogueCacheFilter();

    private static byte[] body(String flashSaleEndsAt) {
        String sale = flashSaleEndsAt == null ? ""
                : ",\"flashSaleEndsAt\":\"" + flashSaleEndsAt + "\"";
        return ("{\"data\":[{\"sku\":\"MBP-14-M4\",\"storePrice\":1500.00" + sale + "}]}")
                .getBytes(StandardCharsets.UTF_8);
    }

    /**
     * A body shaped like a card quoting the PRE-sale price of a sale that has not started yet: no end
     * date to be bounded by, only the start.
     */
    private static byte[] scheduledBody(String flashSaleStartsAt) {
        return ("{\"data\":[{\"sku\":\"MBP-14-M4\",\"storePrice\":1000.00,"
                + "\"flashSaleStartsAt\":\"" + flashSaleStartsAt + "\"}]}")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static FilterChain servingJson(byte[] payload) {
        return (request, response) -> {
            response.setContentType("application/json");
            response.getOutputStream().write(payload);
        };
    }

    private MockHttpServletResponse get(byte[] payload) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", PATH);
        request.setRequestURI(PATH);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, servingJson(payload));
        return response;
    }

    /**
     * A body shaped like a product page: a discounted card price with a struck-through original, and a
     * {@code variants[]} array. Variants carry no prices of their own — the listing's one price applies
     * to every line of it — so the array is here only to prove the marker is found past nested objects.
     *
     * @param flashSaleEndsAt the marker, or null to model a response that quotes the discount and
     *                        FORGETS to say when it ends
     */
    private static byte[] nestedBody(String flashSaleEndsAt) {
        String sale = flashSaleEndsAt == null ? ""
                : ",\"flashSaleEndsAt\":\"" + flashSaleEndsAt + "\"";
        return ("{\"data\":[{\"sku\":\"MBP-14-M4\",\"storePrice\":675.00,\"originalPrice\":900.00,"
                + sale + ",\"variants\":["
                + "{\"sku\":\"MBP-14-M4-512\"},{\"sku\":\"MBP-14-M4-1TB\"}]}]}")
                .getBytes(StandardCharsets.UTF_8);
    }

    // ── The marker is found wherever in the body it sits ─────────────────────

    @Test
    void aBodyWithANestedVariantsArrayStillExpiresAtTheSalesEnd() {
        // A body with a nested variants[] array. The marker sits before it, so this also proves the scan
        // does not need the field to be the last thing in the body — and the filter can only know the body
        // is perishable from that marker.
        byte[] stale = nestedBody(Instant.now().minus(1, ChronoUnit.MINUTES).toString());

        assertDoesNotThrow(() -> {
            get(stale);
            MockHttpServletResponse second = get(stale);
            assertNotEquals("HIT", second.getHeader("X-Catalogue-Cache"),
                    "a discounted variant price must not outlive its sale in the cache either");
        });
    }

    @Test
    void theMarkerIsWhatMakesADiscountedBodyPerishableSoItsAbsenceIsTheDangerousCase() {
        // This is the bug resurrected INSIDE the cache, and the reason buildStoreOption stamps
        // flashSaleEndsAt on every option whose quoted price is discounted by a window that has an end.
        //
        // Without the marker the body is cacheable for the flat 60 seconds regardless of when the sale
        // finishes, which is precisely how a card goes back to advertising a price the cart will not
        // charge. The assertion is deliberately the UNSAFE behaviour: it documents that the filter cannot
        // protect an unmarked body, so the invariant has to be upheld by whoever builds it.
        byte[] unmarked = nestedBody(null);

        assertDoesNotThrow(() -> {
            get(unmarked);
            MockHttpServletResponse second = get(unmarked);
            assertEquals("HIT", second.getHeader("X-Catalogue-Cache"),
                    "an unmarked body is cached on the flat TTL — which is why every response quoting a "
                            + "discounted price must carry flashSaleEndsAt");
        });
    }

    // ── The sale must not disable the cache ──────────────────────────────────

    @Test
    void aPageQuotingALiveSaleIsStillCached() {
        // The defect: one discounted product anywhere in a list page used to make the whole page
        // uncacheable, and a campaign covering the home rails made most of the catalogue uncacheable.
        byte[] payload = body(Instant.now().plus(2, ChronoUnit.HOURS).toString());

        assertDoesNotThrow(() -> {
            get(payload);
            MockHttpServletResponse second = get(payload);
            assertEquals("HIT", second.getHeader("X-Catalogue-Cache"),
                    "a live sale must not switch the cache off");
        });
    }

    @Test
    void aPageQuotingASaleThatHasAlreadyEndedIsNotStored() throws Exception {
        // There is no eviction hook — nothing can reach into this cache when a sale ends — so an entry
        // is only ever as trustworthy as the soonest end date in it.
        byte[] payload = body(Instant.now().minus(1, ChronoUnit.MINUTES).toString());

        get(payload);
        MockHttpServletResponse second = get(payload);

        assertNotEquals("HIT", second.getHeader("X-Catalogue-Cache"),
                "an expired sale price must never be served from memory");
    }

    @Test
    void aSaleBoundedHitIsNotAlsoLicensedToBeShownStaleForFiveMoreMinutes() throws Exception {
        byte[] payload = body(Instant.now().plus(30, ChronoUnit.SECONDS).toString());

        get(payload);
        String cacheControl = get(payload).getHeader("Cache-Control");

        assertNotNull(cacheControl);
        assertFalse(cacheControl.contains("stale-while-revalidate"),
                "the whole point is that this copy stops being showable when the sale stops");
        int maxAge = Integer.parseInt(cacheControl.replaceAll(".*max-age=(\\d+).*", "$1"));
        assertTrue(maxAge <= 30, "max-age must not outlive the sale, was " + maxAge);
    }

    @Test
    void anOrdinaryCataloguePageKeepsTheFullTtlAndTheStaleWindow() throws Exception {
        byte[] payload = body(null);

        get(payload);
        MockHttpServletResponse second = get(payload);

        assertEquals("HIT", second.getHeader("X-Catalogue-Cache"));
        assertEquals("public, max-age=60, stale-while-revalidate=300", second.getHeader("Cache-Control"),
                "nothing about a page with no sale in it has changed");
    }

    @Test
    void aSaleEndingJUSTBEYONDTheTtlStillCutsTheBrowsersLicence() {
        // The hole this closes, and the reason the old test did not see it: the client directive was only
        // cut when the ENTRY's life had been shortened, i.e. when the sale ended inside the 60s TTL. A
        // sale ending 70 seconds out left the flat "max-age=60, stale-while-revalidate=300" in place —
        // which licenses the browser to re-show the sale price past the end and then serve it stale for
        // five minutes more. The card says 750, the basket stamps 1000, and no 409 fires because the line
        // was never stamped at 750: the same inversion, relocated into the browser.
        byte[] payload = body(Instant.now().plus(70, ChronoUnit.SECONDS).toString());

        assertDoesNotThrow(() -> {
            get(payload);
            String cacheControl = get(payload).getHeader("Cache-Control");

            assertNotNull(cacheControl);
            assertFalse(cacheControl.contains("stale-while-revalidate"),
                    "a stale window outlives the sale by five minutes whatever the max-age is: " + cacheControl);
            int maxAge = Integer.parseInt(cacheControl.replaceAll(".*max-age=(\\d+).*", "$1"));
            assertTrue(maxAge <= 70, "max-age must not outlive the sale, was " + maxAge);
        });
    }

    // ── The START of a sale bounds the cache too ─────────────────────────────

    @Test
    void aBodyQuotingThePreSalePriceIsBoundedByTheSalesSTART() {
        // The mirror of an expired sale, and it was missed because such a body carries no end date at
        // all: serialised before the sale opens, it quotes the PRE-sale price, so it used to get the full
        // TTL and the flat directive. For up to six minutes after the sale began, cards showed the old
        // price while the cart already charged the new one — the sale visibly failing to start on the
        // busiest surface in the shop.
        byte[] payload = scheduledBody(Instant.now().plus(30, ChronoUnit.SECONDS).toString());

        assertDoesNotThrow(() -> {
            get(payload);
            String cacheControl = get(payload).getHeader("Cache-Control");

            assertNotNull(cacheControl);
            assertFalse(cacheControl.contains("stale-while-revalidate"),
                    "the pre-sale price stops being showable the moment the sale opens: " + cacheControl);
            int maxAge = Integer.parseInt(cacheControl.replaceAll(".*max-age=(\\d+).*", "$1"));
            assertTrue(maxAge <= 30, "max-age must not outlive the pre-sale price, was " + maxAge);
        });
    }

    @Test
    void aPreSalePriceWhoseSaleHasSinceSTARTEDIsNotStored() throws Exception {
        // Same reasoning as an ended sale: there is no eviction hook, so a body is only as trustworthy
        // as its nearest boundary — in either direction.
        byte[] payload = scheduledBody(Instant.now().minus(1, ChronoUnit.MINUTES).toString());

        get(payload);
        MockHttpServletResponse second = get(payload);

        assertNotEquals("HIT", second.getHeader("X-Catalogue-Cache"),
                "a pre-sale price must not be served after the sale has opened");
    }

    // ── Reading the sale boundaries back out of the bytes ────────────────────

    @Test
    void theSoonestEndInTheBodyIsTheOneThatBounds() {
        Instant soon = Instant.parse("2026-03-31T20:00:00Z");
        Instant later = Instant.parse("2026-04-30T20:00:00Z");
        byte[] payload = ("[{\"flashSaleEndsAt\":\"" + later + "\"},{\"flashSaleEndsAt\":\"" + soon + "\"}]")
                .getBytes(StandardCharsets.UTF_8);

        assertEquals(soon.toEpochMilli(), CatalogueCacheFilter.nextPriceChange(payload),
                "a page is only as cacheable as its first sale to finish");
    }

    @Test
    void theNEARERofAStartAndAnEndIsTheOneThatBounds() {
        // A list page can hold both: one product's sale ending this evening and another's starting in an
        // hour. Whichever comes first is when these bytes stop being true.
        Instant startsSoon = Instant.parse("2026-03-31T18:00:00Z");
        Instant endsLater = Instant.parse("2026-03-31T20:00:00Z");
        byte[] payload = ("[{\"flashSaleEndsAt\":\"" + endsLater + "\"},"
                + "{\"flashSaleStartsAt\":\"" + startsSoon + "\"}]").getBytes(StandardCharsets.UTF_8);

        assertEquals(startsSoon.toEpochMilli(), CatalogueCacheFilter.nextPriceChange(payload));
    }

    @Test
    void anUnreadableStartDateAlsoExpiresImmediately() {
        assertEquals(0, CatalogueCacheFilter.nextPriceChange(
                "{\"flashSaleStartsAt\":null}".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void aBodyWithNoSaleIsUnbounded() {
        assertEquals(Long.MAX_VALUE, CatalogueCacheFilter.nextPriceChange(body(null)));
    }

    @Test
    void anUnreadableEndDateExpiresImmediatelyRatherThanCountingAsAbsent() {
        // Fails towards a shorter cache life on purpose: if date serialization ever changes, this bound
        // must break loudly into "do not cache" rather than quietly into "cache for the full minute".
        assertEquals(0, CatalogueCacheFilter.nextPriceChange(
                "{\"flashSaleEndsAt\":1774900000000}".getBytes(StandardCharsets.UTF_8)));
        assertEquals(0, CatalogueCacheFilter.nextPriceChange(
                "{\"flashSaleEndsAt\":\"next Tuesday\"}".getBytes(StandardCharsets.UTF_8)));
        assertEquals(0, CatalogueCacheFilter.nextPriceChange(
                "{\"flashSaleEndsAt\":null}".getBytes(StandardCharsets.UTF_8)));
    }
}
