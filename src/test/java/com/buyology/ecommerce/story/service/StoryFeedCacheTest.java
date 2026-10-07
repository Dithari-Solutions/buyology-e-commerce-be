package com.buyology.ecommerce.story.service;

import com.buyology.ecommerce.common.enums.Language;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StoryFeedCacheTest {
    @Test void expiresAndInvalidatesLanguageSnapshots() {
        Clock clock = mock(Clock.class);
        long now = Instant.parse("2026-10-07T12:00:00Z").toEpochMilli();
        when(clock.millis()).thenReturn(now);
        StoryFeedCache cache = new StoryFeedCache(clock);
        AtomicInteger calls = new AtomicInteger();
        java.util.function.Supplier<List<com.buyology.ecommerce.story.dto.StorySummaryResponse>> load = () -> { calls.incrementAndGet(); return List.of(); };
        cache.get(Language.EN, load); cache.get(Language.EN, load);
        assertEquals(1, calls.get());
        cache.get(Language.AZ, load); assertEquals(2, calls.get());
        when(clock.millis()).thenReturn(now + 60_001);
        cache.get(Language.EN, load); assertEquals(3, calls.get());
        cache.invalidate(); cache.get(Language.EN, load); assertEquals(4, calls.get());
    }
    @Test void coalescesConcurrentColdLoadsAndDoesNotCacheFailures() throws Exception {
        StoryFeedCache cache = new StoryFeedCache();
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            java.util.function.Supplier<List<com.buyology.ecommerce.story.dto.StorySummaryResponse>> load = () -> {
                calls.incrementAndGet(); entered.countDown();
                try { assertTrue(release.await(5, TimeUnit.SECONDS)); } catch (InterruptedException e) { throw new RuntimeException(e); }
                return List.of();
            };
            Future<?> first = executor.submit(() -> cache.get(Language.EN, load));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            Future<?> second = executor.submit(() -> cache.get(Language.EN, load));
            release.countDown(); first.get(5, TimeUnit.SECONDS); second.get(5, TimeUnit.SECONDS);
            assertEquals(1, calls.get());
        } finally { executor.shutdownNow(); }
        cache.invalidate();
        assertThrows(IllegalStateException.class, () -> cache.get(Language.EN, () -> { throw new IllegalStateException(); }));
        cache.get(Language.EN, () -> { calls.incrementAndGet(); return List.of(); });
        assertEquals(2, calls.get());
    }
}
