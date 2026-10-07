package com.buyology.ecommerce.story.service;

import com.buyology.ecommerce.common.enums.Language;
import com.buyology.ecommerce.story.dto.StorySummaryResponse;
import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Three public language snapshots, never containing a user's likes. Cold loads are coalesced. */
final class StoryFeedCache {
    private static final long TTL_MS = 60_000;
    private record Entry(List<StorySummaryResponse> stories, long expiresAt) {}
    private final Clock clock;
    private final Map<Language, Entry> entries = new EnumMap<>(Language.class);
    private final Map<Language, Object> locks = new EnumMap<>(Language.class);

    StoryFeedCache() { this(Clock.systemUTC()); }
    StoryFeedCache(Clock clock) {
        this.clock = clock;
        for (Language language : Language.values()) locks.put(language, new Object());
    }
    List<StorySummaryResponse> get(Language language, Supplier<List<StorySummaryResponse>> load) {
        synchronized (locks.get(language)) {
            Entry hit;
            synchronized (entries) { hit = entries.get(language); }
            if (hit != null && hit.expiresAt() > clock.millis()) return hit.stories();
            List<StorySummaryResponse> stories = List.copyOf(load.get());
            synchronized (entries) { entries.put(language, new Entry(stories, clock.millis() + TTL_MS)); }
            return stories;
        }
    }
    void invalidate() {
        for (Language language : Language.values()) {
            synchronized (locks.get(language)) { synchronized (entries) { entries.remove(language); } }
        }
    }
}
