package com.buyology.ecommerce.story.domain;

import java.util.UUID;

/**
 * Thrown when a Story with the given ID is not found.
 */
public class StoryNotFoundException extends RuntimeException {

    private final UUID storyId;

    public StoryNotFoundException(UUID storyId) {
        super("Story not found with id: " + storyId);
        this.storyId = storyId;
    }

    public UUID getStoryId() {
        return storyId;
    }
}
