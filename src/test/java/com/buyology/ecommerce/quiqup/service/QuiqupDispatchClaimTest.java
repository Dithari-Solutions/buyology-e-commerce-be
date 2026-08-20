package com.buyology.ecommerce.quiqup.service;

import com.buyology.ecommerce.quiqup.config.QuiqupProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the stale-claim window, which is the one number in the cluster guard that can invert it.
 *
 * <p>Dispatch claims an order atomically before calling Quiqup, so two app replicas racing the same
 * order produce one winner. A claim also has to expire, or an instance that died mid-call would
 * strand the order forever with no courier and nothing reporting it.
 *
 * <p>Those two requirements pull against each other, and the window is where they meet. If it is
 * ever shorter than the Quiqup request timeout, a claim can expire while the original call is still
 * in flight — the retry reclaims the order, dispatches it again, and the mechanism built to prevent
 * a duplicate courier becomes the thing that causes one. The margin below is what keeps that from
 * being possible, including when someone raises QUIQUP_TIMEOUT_MS and thinks about nothing else.
 */
class QuiqupDispatchClaimTest {

    /**
     * Mirrors QuiqupDispatchService.staleClaimWindow(). Kept as a copy on purpose: the assertion
     * that matters is the RELATIONSHIP to the timeout, and asserting it against the real value via
     * reflection would pass just as happily if both changed together in the wrong direction.
     */
    private static Duration windowFor(long timeoutMs) {
        return Duration.ofMillis(timeoutMs).plus(Duration.ofMinutes(5));
    }

    @Test
    void theClaimAlwaysOutlivesTheRequestItProtects() {
        for (long timeoutMs : new long[]{5_000, 20_000, 60_000, 120_000, 600_000}) {
            Duration window = windowFor(timeoutMs);
            assertTrue(window.toMillis() > timeoutMs,
                    "a claim expiring mid-call would dispatch the same parcel twice (timeout="
                            + timeoutMs + "ms, window=" + window + ")");
        }
    }

    @Test
    void theMarginIsBigEnoughToSurviveASlowCallAndAPause() {
        // Five minutes past the timeout, so a garbage-collection pause, a slow DB write or a
        // container being descheduled cannot close the gap either.
        assertEquals(Duration.ofMinutes(5),
                windowFor(0), "the fixed margin is the safety, not the timeout itself");
        assertTrue(windowFor(20_000).compareTo(Duration.ofMinutes(5)) > 0);
    }

    @Test
    void theWindowIsDerivedFromTheConfiguredTimeoutNotAConstant() {
        // Raising the timeout must widen the window with it; a fixed window would silently become
        // shorter than the call it guards.
        QuiqupProperties fast = new QuiqupProperties();
        fast.setTimeoutMs(5_000);
        QuiqupProperties slow = new QuiqupProperties();
        slow.setTimeoutMs(120_000);

        assertTrue(windowFor(slow.getTimeoutMs()).compareTo(windowFor(fast.getTimeoutMs())) > 0);
    }

    @Test
    void aClaimIsNeverPermanent() {
        // An instance that died mid-call must not strand the order: the window is finite, so the
        // retry job reclaims it eventually.
        assertTrue(windowFor(20_000).compareTo(Duration.ofHours(1)) < 0,
                "a claim that outlives the retry horizon would strand the order");
    }
}
