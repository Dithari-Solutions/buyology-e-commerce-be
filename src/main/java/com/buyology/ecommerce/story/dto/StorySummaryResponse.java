package com.buyology.ecommerce.story.dto;

import java.util.List;
import java.util.UUID;

public record StorySummaryResponse(
        UUID id,
        String title,
        String thumbnailUrl,
        List<StoryResponse.MediaItem> media) {
}
