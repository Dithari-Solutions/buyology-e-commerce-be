package com.buyology.ecommerce.quiqup;

import com.buyology.ecommerce.quiqup.config.QuiqupProperties;
import com.buyology.ecommerce.quiqup.controller.QuiqupWebhookController;
import com.buyology.ecommerce.quiqup.repository.QuiqupTestEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Quiqup publish no signature specification. Support have confirmed only the hash family ("we use
 * SHA256"), which leaves the header name, the encoding, and what string is actually signed unknown —
 * and a signature check needs all three.
 *
 * <p>So the webhook endpoint computes every plausible signature and reports which one matched. These
 * tests prove it recognises each construction <em>under a header name we did not anticipate</em>,
 * which is the whole point: a fixed list of guessed header names is how a correct secret ends up
 * reading as "invalid".
 */
class QuiqupSignatureIdentificationTest {

    private static final String SECRET = "quiqup-staging-shared-secret";
    private static final String BODY = "{\"event\":\"order.picked_up\",\"id\":\"ORD-123\"}";

    private final QuiqupProperties props = new QuiqupProperties();

    private QuiqupWebhookController controller() {
        props.setWebhookSecret(SECRET);
        return new QuiqupWebhookController(
                props, mock(QuiqupTestEventRepository.class), new ObjectMapper(), inertDispatch());
    }

    /** {@code identifySignature} is private — invoked reflectively so production stays unexported. */
    private Object identify(HttpServletRequest request) {
        return ReflectionTestUtils.invokeMethod(controller(), "identifySignature", request, BODY);
    }

    private MockHttpServletRequest webhook(String headerName, String headerValue) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/quiqup/webhook");
        request.setContent(BODY.getBytes(StandardCharsets.UTF_8));
        request.addHeader("Content-Type", "application/json");
        request.addHeader(headerName, headerValue);
        return request;
    }

    private static byte[] hmac(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static String hex(byte[] b) {
        return HexFormat.of().formatHex(b);
    }

    @Test
    void identifiesHmacHexUnderAnUnanticipatedHeaderName() throws Exception {
        String signature = hex(hmac(SECRET.getBytes(StandardCharsets.UTF_8),
                BODY.getBytes(StandardCharsets.UTF_8)));

        // Deliberately NOT one of the names the old implementation guessed.
        Object match = identify(webhook("Quiqup-Delivery-Sign", signature));

        assertNotNull(match, "a correct HMAC must be found whatever the header is called");
        assertTrue(match.toString().contains("Quiqup-Delivery-Sign"));
        assertTrue(match.toString().contains("HMAC_SHA256(body) hex"), match.toString());
    }

    @Test
    void identifiesHmacBase64AndToleratesAnAlgorithmPrefix() throws Exception {
        String signature = Base64.getEncoder().encodeToString(
                hmac(SECRET.getBytes(StandardCharsets.UTF_8), BODY.getBytes(StandardCharsets.UTF_8)));

        Object match = identify(webhook("X-Signature", "sha256=" + signature));

        assertNotNull(match);
        assertTrue(match.toString().contains("base64"), match.toString());
    }

    @Test
    void identifiesAPlainDigestOfSecretAndBody() throws Exception {
        // "We use SHA256" may literally mean a digest rather than a keyed MAC.
        byte[] concatenated = (SECRET + BODY).getBytes(StandardCharsets.UTF_8);
        String signature = hex(MessageDigest.getInstance("SHA-256").digest(concatenated));

        Object match = identify(webhook("X-Hash", signature));

        assertNotNull(match);
        assertTrue(match.toString().contains("SHA256(secret+body) hex"), match.toString());
    }

    @Test
    void identifiesTimestampPrefixedSigningWhenATimestampHeaderIsSent() throws Exception {
        String timestamp = "1786400000";
        String signature = hex(hmac(SECRET.getBytes(StandardCharsets.UTF_8),
                (timestamp + "." + BODY).getBytes(StandardCharsets.UTF_8)));

        MockHttpServletRequest request = webhook("X-Quiqup-Signature", signature);
        request.addHeader("X-Quiqup-Timestamp", timestamp);

        Object match = identify(request);

        assertNotNull(match, "timestamp-prefixed signing must be recognised too");
        assertTrue(match.toString().contains("X-Quiqup-Timestamp"), match.toString());
    }

    @Test
    void reportsNoMatchForAWrongSignatureRatherThanGuessing() {
        Object match = identify(webhook("X-Quiqup-Signature", "0".repeat(64)));

        assertNull(match, "an unrecognised signature must not be reported as identified");
    }

    @Test
    void reportsNoMatchWhenNoSecretIsConfigured() throws Exception {
        String signature = hex(hmac(SECRET.getBytes(StandardCharsets.UTF_8),
                BODY.getBytes(StandardCharsets.UTF_8)));
        props.setWebhookSecret("");

        QuiqupWebhookController noSecret = new QuiqupWebhookController(
                props, mock(QuiqupTestEventRepository.class), new ObjectMapper(), inertDispatch());
        Object match = ReflectionTestUtils.invokeMethod(
                noSecret, "identifySignature", webhook("X-Quiqup-Signature", signature), BODY);

        assertNull(match, "with no secret there is nothing to verify against");
    }

    /**
     * A delivery-status service that does nothing.
     *
     * <p>These tests are about signature verification, not about orders. Dispatch is off by
     * default, and {@code apply} returns before touching anything when it is — so the null
     * collaborators are unreachable rather than merely unused.
     */
    private static com.buyology.ecommerce.quiqup.service.QuiqupDeliveryStatusService inertDispatch() {
        return new com.buyology.ecommerce.quiqup.service.QuiqupDeliveryStatusService(
                new QuiqupProperties(), null, null);
    }
}
