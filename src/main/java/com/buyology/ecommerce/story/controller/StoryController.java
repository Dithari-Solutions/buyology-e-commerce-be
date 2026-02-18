package com.buyology.ecommerce.story.controller;

import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.buyology.ecommerce.story.domain.Story;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.buyology.ecommerce.common.enums.Language;
import com.buyology.ecommerce.story.dto.StoryResponse;
import org.springframework.web.multipart.MultipartFile;
import com.buyology.ecommerce.story.service.StoryService;
import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.story.dto.CreateStoryRequest;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.buyology.ecommerce.story.dto.StorySummaryResponse;

@RestController
@RequestMapping("/api/story")
@Tag(name = "Story", description = "APIs for stories")
public class StoryController {

    private final StoryService storyService;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    public StoryController(StoryService storyService) {
        this.storyService = storyService;
    }

    @Operation(summary = "Create a new story")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Story> createStory(
            @RequestPart("request") String requestJson,
            @RequestPart("mediaFiles") List<MultipartFile> mediaFiles,
            @RequestHeader("X-User-Id") UUID createdBy) throws Exception {

        CreateStoryRequest request = objectMapper.readValue(requestJson, CreateStoryRequest.class);
        Story story = storyService.createStory(request, mediaFiles, createdBy);
        return ResponseEntity.ok(story);
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<StorySummaryResponse>>> getStories(
            @RequestParam Language language) {
        return storyService.getStories(language);
    }

    @GetMapping("/{storyId}")
    public ResponseEntity<ApiResponse<StoryResponse>> getStoryDetails(
            @RequestParam Language language,
            @PathVariable UUID storyId) {
        return storyService.getStoryDetails(language, storyId);
    }

    @PostMapping("/{storyId}/activate")
    public ResponseEntity<ApiResponse<Void>> activateStory(@PathVariable UUID storyId) {
        storyService.activateStory(storyId);
        return ApiResponse.success(null, "Story activated successfully.");
    }

    @PostMapping("/{storyId}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deActivateStory(@PathVariable UUID storyId) {
        storyService.deActivateStory(storyId);
        return ApiResponse.success(null, "Story deactivated successfully.");
    }

    @Operation(summary = "Add media files to an existing story")
    @PostMapping(value = "/{storyId}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Void>> addMediaToStory(
            @PathVariable UUID storyId,
            @RequestPart("mediaFiles") List<MultipartFile> mediaFiles) {
        storyService.addMediaToStory(storyId, mediaFiles);
        return ApiResponse.success(null, "Media added to story successfully.");
    }

    @Operation(summary = "Delete a story with all its translations and media")
    @DeleteMapping("/{storyId}")
    public ResponseEntity<ApiResponse<Void>> deleteStory(@PathVariable UUID storyId) {
        storyService.deleteStory(storyId);
        return ApiResponse.success(null, "Story deleted successfully.");
    }
}
