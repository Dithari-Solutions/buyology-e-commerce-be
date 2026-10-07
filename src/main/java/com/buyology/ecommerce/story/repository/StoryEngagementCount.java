package com.buyology.ecommerce.story.repository;

import java.util.UUID;

/** Aggregate projection so a feed needs two count queries, rather than two per story. */
public interface StoryEngagementCount {
    UUID getStoryId();
    long getTotal();
}
