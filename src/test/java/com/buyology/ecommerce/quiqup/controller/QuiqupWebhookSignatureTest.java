package com.buyology.ecommerce.quiqup.controller;

import com.buyology.ecommerce.quiqup.config.QuiqupProperties;
import com.buyology.ecommerce.quiqup.domain.QuiqupTestEvent;
import com.buyology.ecommerce.quiqup.repository.QuiqupTestEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * The webhook signature scheme Quiqup confirmed in writing (2026-08-12):
 *
 * <pre>
 *   X-Quiqup-Signature = hex( HMAC-SHA256( subscription secret,
 *                                          X-Quiqup-Timestamp + "." + raw body ) )
 * </pre>
 *
 * <p>These tests build the expected value the same way their PHP reference does
 * ({@code hash_hmac('sha256', $timestamp . '.' . $rawBody, $secret)}) so the implementation is pinned
 * against their spec rather than against itself.
 *
 * <p>Quiqup's published documentation still describes an older SHA-1 scheme and is wrong; support
 * confirmed the above. If someone later "fixes" the implementation to match those docs, these tests
 * are what should stop them.
 */
class QuiqupWebhookSignatureTest {

    private static final String SECRET = "sub_whsec_2f8c41d9e7b04a6f";
    private static final String TIMESTAMP = "1786500000";
    private static final String BODY =
            "{\"event\":\"order.delivery_failed\",\"order\":{\"id\":26010185,\"state\":\"delivery_failed\"}}";

    private final QuiqupProperties props = new QuiqupProperties();

    private QuiqupWebhookController controller() {
        props.setEnabled(true);
        props.setWebhookSecret(SECRET);
        return new QuiqupWebhookController(
                props, mock(QuiqupTestEventRepository.class), new ObjectMapper(), inertDispatch());
    }

    /** Exactly their PHP: hash_hmac('sha256', $timestamp . '.' . $rawBody, $secret). */
    private static String quiqupSignature(String secret, String timestamp, byte[] rawBody) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
        mac.update(rawBody);
        return HexFormat.of().formatHex(mac.doFinal());
    }

    private MockHttpServletRequest delivery(byte[] body, String timestamp, String signature) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/quiqup/webhook");
        request.setContent(body);
        request.addHeader("Content-Type", "application/json");
        if (timestamp != null) request.addHeader("X-Quiqup-Timestamp", timestamp);
        if (signature != null) request.addHeader("X-Quiqup-Signature", signature);
        return request;
    }

    private Boolean verifySignatureOf(MockHttpServletRequest request, byte[] body) {
        return controller().verifySignature(request, body, new String(body, StandardCharsets.UTF_8));
    }

    @Test
    void verifiesASignatureBuiltTheWayQuiqupBuildsIt() throws Exception {
        byte[] body = BODY.getBytes(StandardCharsets.UTF_8);
        String signature = quiqupSignature(SECRET, TIMESTAMP, body);

        assertEquals(Boolean.TRUE, verifySignatureOf(delivery(body, TIMESTAMP, signature), body));
    }

    @Test
    void acceptsAnUppercaseHexSignature() throws Exception {
        byte[] body = BODY.getBytes(StandardCharsets.UTF_8);
        String signature = quiqupSignature(SECRET, TIMESTAMP, body).toUpperCase(java.util.Locale.ROOT);

        assertEquals(Boolean.TRUE, verifySignatureOf(delivery(body, TIMESTAMP, signature), body),
                "hex case must not decide validity");
    }

    @Test
    void rejectsWhenTheTimestampIsNotPartOfTheSignedString() throws Exception {
        // Signing the body alone is the mistake to guard against — the timestamp prefix is required.
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String bodyOnly = HexFormat.of().formatHex(mac.doFinal(BODY.getBytes(StandardCharsets.UTF_8)));
        byte[] body = BODY.getBytes(StandardCharsets.UTF_8);

        assertEquals(Boolean.FALSE, verifySignatureOf(delivery(body, TIMESTAMP, bodyOnly), body));
    }

    @Test
    void rejectsADeliveryMissingEitherHeader() throws Exception {
        byte[] body = BODY.getBytes(StandardCharsets.UTF_8);
        String signature = quiqupSignature(SECRET, TIMESTAMP, body);

        assertEquals(Boolean.FALSE, verifySignatureOf(delivery(body, null, signature), body),
                "without the timestamp the signed string cannot be rebuilt");
        assertEquals(Boolean.FALSE, verifySignatureOf(delivery(body, TIMESTAMP, null), body));
    }

    @Test
    void rejectsTheWrongSecret() throws Exception {
        byte[] body = BODY.getBytes(StandardCharsets.UTF_8);
        // A production subscription's secret against a staging delivery — the failure Quiqup described.
        String signature = quiqupSignature("secret-from-the-other-environment", TIMESTAMP, body);

        assertEquals(Boolean.FALSE, verifySignatureOf(delivery(body, TIMESTAMP, signature), body));
    }

    @Test
    void returnsNullWhenNoSecretIsConfiguredSoTheAdminPageCanSaySoDistinctly() throws Exception {
        byte[] body = BODY.getBytes(StandardCharsets.UTF_8);
        String signature = quiqupSignature(SECRET, TIMESTAMP, body);
        props.setEnabled(true);
        props.setWebhookSecret("");

        QuiqupWebhookController noSecret = new QuiqupWebhookController(
                props, mock(QuiqupTestEventRepository.class), new ObjectMapper(), inertDispatch());
        Boolean result = noSecret.verifySignature(delivery(body, TIMESTAMP, signature), body, BODY);

        assertNull(result, "'not configured' must stay distinguishable from 'did not verify'");
    }

    /**
     * The trap Quiqup called out: signing anything other than the bytes as received.
     *
     * <p>A body carrying a malformed UTF-8 byte survives {@code byte[]} handling untouched, but
     * decoding it to a String and re-encoding replaces that byte with U+FFFD and changes the digest.
     * This asserts the implementation signs the original bytes, so the same delivery verifies either
     * way — the reason the controller keeps the body as a {@code byte[]}.
     */
    @Test
    void verifiesABodyThatIsNotValidUtf8() throws Exception {
        byte[] body = concat(
                "{\"event\":\"order.collected\",\"note\":\"".getBytes(StandardCharsets.UTF_8),
                new byte[]{(byte) 0xC3, (byte) 0x28},                       // invalid UTF-8 sequence
                "\"}".getBytes(StandardCharsets.UTF_8));
        String signature = quiqupSignature(SECRET, TIMESTAMP, body);

        // Proof the round-trip really is lossy, i.e. that this test would catch the regression.
        byte[] roundTripped = new String(body, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8);
        assertFalse(java.util.Arrays.equals(body, roundTripped),
                "test is only meaningful if String round-tripping alters these bytes");

        assertEquals(Boolean.TRUE, verifySignatureOf(delivery(body, TIMESTAMP, signature), body),
                "the signature must be computed over the bytes as received, not a re-encoded copy");
    }

    /**
     * A rejected delivery must still be recorded.
     *
     * <p>It is the delivery most worth inspecting, and the admin Webhooks tab is the only place it is
     * visible regardless of which instance behind the load balancer received it. Returning 401 before
     * persisting would leave nothing but a log line on one of two containers.
     */
    @Test
    void aRejectedDeliveryIsStillStoredBeforeThe401() throws Exception {
        byte[] body = BODY.getBytes(StandardCharsets.UTF_8);
        String wrongSignature = quiqupSignature("the-wrong-secret", TIMESTAMP, body);
        props.setEnabled(true);
        props.setWebhookSecret(SECRET);
        props.setWebhookRequireSignature(true);

        QuiqupTestEventRepository repository = mock(QuiqupTestEventRepository.class);
        QuiqupWebhookController controller =
                new QuiqupWebhookController(props, repository, new ObjectMapper(), inertDispatch());

        var response = controller.receive(delivery(body, TIMESTAMP, wrongSignature));

        assertEquals(401, response.getStatusCode().value(), "a rejected delivery must not answer 200");
        ArgumentCaptor<QuiqupTestEvent> saved = ArgumentCaptor.forClass(QuiqupTestEvent.class);
        verify(repository).save(saved.capture());
        assertEquals(Boolean.FALSE, saved.getValue().getHmacValid(),
                "the stored event must record that the signature failed");
    }

    @Test
    void aValidDeliveryIsAcceptedWithA200EvenWhenRejectionIsArmed() throws Exception {
        byte[] body = BODY.getBytes(StandardCharsets.UTF_8);
        props.setEnabled(true);
        props.setWebhookSecret(SECRET);
        props.setWebhookRequireSignature(true);

        QuiqupTestEventRepository repository = mock(QuiqupTestEventRepository.class);
        QuiqupWebhookController controller =
                new QuiqupWebhookController(props, repository, new ObjectMapper(), inertDispatch());

        var response = controller.receive(
                delivery(body, TIMESTAMP, quiqupSignature(SECRET, TIMESTAMP, body)));

        assertEquals(200, response.getStatusCode().value());
        verify(repository).save(org.mockito.ArgumentMatchers.any());
    }

    private static byte[] concat(byte[]... parts) {
        int length = 0;
        for (byte[] p : parts) length += p.length;
        byte[] out = new byte[length];
        int offset = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, offset, p.length);
            offset += p.length;
        }
        return out;
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
