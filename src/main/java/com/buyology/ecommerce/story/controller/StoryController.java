package com.buyology.ecommerce.story.controller;

import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.buyology.ecommerce.common.enums.Language;
import com.buyology.ecommerce.story.dto.StoryResponse;
import com.buyology.ecommerce.story.service.StoryService;
import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.story.dto.StorySummaryResponse;

@RestController
@RequestMapping("/api/story")
@Tag(name = "Story", description = "Public APIs for stories")
public class StoryController {

    private final StoryService storyService;

    public StoryController(StoryService storyService) {
        this.storyService = storyService;
    }

    @Operation(summary = "Get all active stories")
    @GetMapping
    public ResponseEntity<ApiResponse<List<StorySummaryResponse>>> getStories(
            @RequestParam Language language) {
        return storyService.getPublicStories(language);
    }

    @Operation(summary = "Get active story details")
    @GetMapping("/{storyId}")
    public ResponseEntity<ApiResponse<StoryResponse>> getStoryDetails(
            @RequestParam Language language,
            @PathVariable UUID storyId) {
        return storyService.getPublicStoryDetails(language, storyId);
    }
}
