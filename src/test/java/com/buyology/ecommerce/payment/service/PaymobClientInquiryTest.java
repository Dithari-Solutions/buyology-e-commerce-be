package com.buyology.ecommerce.payment.service;

import com.buyology.ecommerce.payment.exception.PaymentGatewayException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Pins the request that finds a payment at Paymob when no callback gave us its transaction id.
 *
 * <p>Paymob's Transaction Inquiry takes Paymob's own order id and a Bearer token. The old lookup
 * sent only our reference with the token in the body — and paid Tabby and Tamara payments came back
 * as "no such transaction", which the sweep read as "the shopper never paid".
 */
class PaymobClientInquiryTest {

    private static final String BASE = "https://uae.paymob.com";
    private static final String TOKENS = BASE + "/api/auth/tokens";
    private static final String INQUIRY = BASE + "/api/ecommerce/orders/transaction_inquiry";

    private MockRestServiceServer server;
    private PaymobClient client;

    @BeforeEach
    void setUp() {
        RestTemplate rest = new RestTemplate();
        server = MockRestServiceServer.bindTo(rest).build();
        client = new PaymobClient(rest, new ObjectMapper());
    }

    private void tokenIssued(ExpectedCount count) {
        server.expect(count, requestTo(TOKENS))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.api_key").value("api-key"))
                .andRespond(withSuccess("{\"token\":\"tok-1\"}", MediaType.APPLICATION_JSON));
    }

    @Test
    void asksByPaymobsOrderIdWithABearerTokenAndTheTokenInTheBodyToo() {
        tokenIssued(ExpectedCount.once());
        server.expect(requestTo(INQUIRY))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer tok-1"))
                .andExpect(jsonPath("$.auth_token").value("tok-1"))
                .andExpect(jsonPath("$.order_id").value(412345678))
                .andExpect(jsonPath("$.merchant_order_id").doesNotExist())
                .andRespond(withSuccess("{\"id\":31723298,\"success\":true,\"order\":{\"id\":412345678}}",
                        MediaType.APPLICATION_JSON));

        JsonNode found = client.inquireTransaction("api-key", BASE, 412345678L, "our-reference");

        assertEquals(31723298L, found.get("id").asLong());
        server.verify();
    }

    @Test
    void fallsBackToOurReferenceWhenThereIsNoPaymobOrderId() {
        tokenIssued(ExpectedCount.once());
        server.expect(requestTo(INQUIRY))
                .andExpect(jsonPath("$.merchant_order_id").value("our-reference"))
                .andExpect(jsonPath("$.order_id").doesNotExist())
                .andRespond(withSuccess("{\"id\":5}", MediaType.APPLICATION_JSON));

        assertNotNull(client.inquireTransaction("api-key", BASE, null, "our-reference"));
        server.verify();
    }

    @Test
    void reusesTheTokenAcrossLookups() {
        // The sweep asks about up to 40 orders a run; one token serves them all.
        tokenIssued(ExpectedCount.once());
        server.expect(ExpectedCount.twice(), requestTo(INQUIRY))
                .andRespond(withSuccess("{\"id\":5}", MediaType.APPLICATION_JSON));

        client.inquireTransaction("api-key", BASE, 1L, null);
        client.inquireTransaction("api-key", BASE, 2L, null);
        server.verify();
    }

    @Test
    void nothingThereIsAnAnswerNotAnError() {
        tokenIssued(ExpectedCount.once());
        server.expect(requestTo(INQUIRY)).andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo(INQUIRY)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertNull(client.inquireTransaction("api-key", BASE, 1L, null), "404 means no transaction");
        assertNull(client.inquireTransaction("api-key", BASE, 2L, null), "an empty reply means no transaction");
        server.verify();
    }

    @Test
    void refusedCredentialsAreAnErrorNeverANotPaid() {
        // Refused, a fresh token minted, refused again: that is real, and it is an error.
        server.expect(requestTo(TOKENS)).andRespond(withSuccess("{\"token\":\"tok-1\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(INQUIRY))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"detail\":\"invalid token\"}"));
        server.expect(requestTo(TOKENS)).andRespond(withSuccess("{\"token\":\"tok-2\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(INQUIRY))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"detail\":\"invalid token\"}"));

        PaymentGatewayException e = assertThrows(PaymentGatewayException.class,
                () -> client.inquireTransaction("api-key", BASE, 1L, null));
        assertTrue(e.getMessage().contains("401"), e.getMessage());
        server.verify();
    }

    @Test
    void aCachedTokenPaymobStoppedHonouringIsRenewedOnce() {
        server.expect(requestTo(TOKENS)).andRespond(withSuccess("{\"token\":\"old\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(INQUIRY)).andExpect(header("Authorization", "Bearer old"))
                .andRespond(withSuccess("{\"id\":1}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(INQUIRY)).andExpect(header("Authorization", "Bearer old"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo(TOKENS)).andRespond(withSuccess("{\"token\":\"new\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(INQUIRY)).andExpect(header("Authorization", "Bearer new"))
                .andRespond(withSuccess("{\"id\":2}", MediaType.APPLICATION_JSON));

        client.inquireTransaction("api-key", BASE, 1L, null);
        assertEquals(2L, client.inquireTransaction("api-key", BASE, 2L, null).get("id").asLong());
        server.verify();
    }

    @Test
    void aMissingApiKeyIsAnErrorNotASilentNothing() {
        assertThrows(PaymentGatewayException.class, () -> client.inquireTransaction("", BASE, 1L, null));
        server.verify();   // nothing was sent
    }

    @Test
    void noIdentifierMeansNothingToAsk() {
        assertNull(client.inquireTransaction("api-key", BASE, null, " "));
        server.verify();
    }
}
