package com.buyology.ecommerce.quiqup;

import com.buyology.ecommerce.quiqup.service.QuiqupSamples;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code partner_order_id} is our side of the correlation with Quiqup — it is what identifies an
 * order in our system when a delivery event or an invoice line comes back. It used to be a constant
 * in the prefill, so every order created from the admin page reached Quiqup as {@code TEST-OD-0001}
 * and their dashboard showed four test orders that were indistinguishable by the one column meant to
 * tell them apart.
 */
class QuiqupSamplesTest {

    private final QuiqupSamples samples = new QuiqupSamples(new ObjectMapper());

    @Test
    void eachFetchCarriesADistinctPartnerReference() {
        String first = samples.all().get("onDemand").get("partner_order_id").asText();
        String second = samples.all().get("onDemand").get("partner_order_id").asText();

        assertNotEquals(first, second, "two creates must not land on Quiqup with the same reference");
        assertTrue(first.startsWith("TEST-OD-"), first);
    }

    @Test
    void theOrderPresetsAreDistinctFromEachOther() {
        Map<String, JsonNode> all = samples.all();

        assertNotEquals(
                all.get("onDemand").get("partner_order_id").asText(),
                all.get("ecommerce").get("partner_order_id").asText());
        assertTrue(all.get("ecommerce").get("partner_order_id").asText().startsWith("TEST-EC-"));
    }

    @Test
    void theQuotePresetIsLeftAloneBecauseAQuoteIsNotAnOrder() {
        assertFalse(samples.all().get("quote").has("partner_order_id"));
    }

    @Test
    void everyPresetIsStillValidJsonWithTheFieldsTheCreateCallNeeds() {
        JsonNode onDemand = samples.all().get("onDemand");

        // Sanity-check the shape Quiqup accepted for order 26010185.
        assertEquals("partner_4hr", onDemand.get("kind").asText());
        assertTrue(onDemand.get("origin").get("address").get("coords").isArray());
        assertTrue(onDemand.get("destination").get("address").get("coords").isArray());
        assertTrue(onDemand.get("items").isArray());
    }
}
