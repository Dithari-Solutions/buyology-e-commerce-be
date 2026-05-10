package com.buyology.ecommerce.story.controller;

import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.buyology.ecommerce.common.enums.Language;
import com.buyology.ecommerce.common.utils.SecurityUtils;
import com.buyology.ecommerce.story.dto.StoryLikeResponse;
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
        return storyService.getPublicStories(language, SecurityUtils.currentUserIdOrNull());
    }

    @Operation(summary = "Get active story details")
    @GetMapping("/{storyId}")
    public ResponseEntity<ApiResponse<StoryResponse>> getStoryDetails(
            @RequestParam Language language,
            @PathVariable UUID storyId) {
        return storyService.getPublicStoryDetails(language, storyId, SecurityUtils.currentUserIdOrNull());
    }

    @Operation(summary = "Record a view for a story (authenticated by user-id or hashed IP)")
    @PostMapping("/{storyId}/view")
    public ResponseEntity<ApiResponse<Void>> recordView(
            @PathVariable UUID storyId,
            HttpServletRequest request) {
        UUID userId = SecurityUtils.currentUserIdOrNull();
        String viewerHash = userId == null
                ? SecurityUtils.sha256Hex(SecurityUtils.clientIp(request))
                : null;
        return storyService.recordView(storyId, userId, viewerHash);
    }

    @Operation(summary = "Like a story (authentication required)")
    @PostMapping("/{storyId}/like")
    public ResponseEntity<ApiResponse<StoryLikeResponse>> likeStory(@PathVariable UUID storyId) {
        UUID userId = SecurityUtils.currentUserIdOrNull();
        if (userId == null) {
            return ApiResponse.failure(HttpStatus.UNAUTHORIZED, "Sign in to like stories");
        }
        return storyService.likeStory(storyId, userId);
    }

    @Operation(summary = "Remove a like from a story (authentication required)")
    @DeleteMapping("/{storyId}/like")
    public ResponseEntity<ApiResponse<StoryLikeResponse>> unlikeStory(@PathVariable UUID storyId) {
        UUID userId = SecurityUtils.currentUserIdOrNull();
        if (userId == null) {
            return ApiResponse.failure(HttpStatus.UNAUTHORIZED, "Sign in to manage likes");
        }
        return storyService.unlikeStory(storyId, userId);
    }
}
