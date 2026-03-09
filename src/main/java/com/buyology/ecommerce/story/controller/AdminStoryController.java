package com.buyology.ecommerce.story.controller;

import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.buyology.ecommerce.common.enums.Language;
import com.buyology.ecommerce.story.dto.CreateStoryFormData;
import com.buyology.ecommerce.story.dto.StoryResponse;
import org.springframework.web.multipart.MultipartFile;
import com.buyology.ecommerce.story.service.StoryService;
import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.story.dto.CreateStoryRequest;
import com.buyology.ecommerce.story.dto.StorySummaryResponse;

@RestController
@RequestMapping("/api/admin/story")
@Tag(name = "Admin Story", description = "Admin APIs for story management")
public class AdminStoryController {

    private final StoryService storyService;
    private final ObjectMapper objectMapper;

    public AdminStoryController(StoryService storyService, ObjectMapper objectMapper) {
        this.storyService = storyService;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "Get all stories with status")
    @GetMapping
    public ResponseEntity<ApiResponse<List<StorySummaryResponse>>> getStories(
            @RequestParam Language language) {
        return storyService.getAdminStories(language);
    }

    @Operation(summary = "Get story details with status")
    @GetMapping("/{storyId}")
    public ResponseEntity<ApiResponse<StoryResponse>> getStoryDetails(
            @RequestParam Language language,
            @PathVariable UUID storyId) {
        return storyService.getAdminStoryDetails(language, storyId);
    }

    @Operation(summary = "Create a new story")
    @RequestBody(
        required = true,
        content = @Content(
            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
            schema = @Schema(implementation = CreateStoryFormData.class),
            encoding = @Encoding(name = "request", contentType = MediaType.APPLICATION_JSON_VALUE)
        )
    )
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<StoryResponse>> createStory(
            @org.springframework.web.bind.annotation.RequestPart("request") String requestJson,
            @org.springframework.web.bind.annotation.RequestPart("thumbnail") MultipartFile thumbnail,
            @org.springframework.web.bind.annotation.RequestPart("mediaFiles") List<MultipartFile> mediaFiles,
            @RequestHeader("X-User-Id") UUID createdBy) throws Exception {

        CreateStoryRequest request = objectMapper.readValue(requestJson, CreateStoryRequest.class);
        StoryResponse response = StoryResponse.from(
                storyService.createStory(request, thumbnail, mediaFiles, createdBy), Language.AZ);
        return ApiResponse.created(response, "Story created successfully");
    }

    @Operation(summary = "Add media files to an existing story")
    @PostMapping(value = "/{storyId}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Void>> addMediaToStory(
            @PathVariable UUID storyId,
            @RequestPart("mediaFiles") List<MultipartFile> mediaFiles) {
        storyService.addMediaToStory(storyId, mediaFiles);
        return ApiResponse.success(null, "Media added to story successfully.");
    }

    @Operation(summary = "Activate a story")
    @PostMapping("/{storyId}/activate")
    public ResponseEntity<ApiResponse<Void>> activateStory(@PathVariable UUID storyId) {
        storyService.activateStory(storyId);
        return ApiResponse.success(null, "Story activated successfully.");
    }

    @Operation(summary = "Deactivate a story")
    @PostMapping("/{storyId}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deActivateStory(@PathVariable UUID storyId) {
        storyService.deActivateStory(storyId);
        return ApiResponse.success(null, "Story deactivated successfully.");
    }

    @Operation(summary = "Delete a story with all its translations and media")
    @DeleteMapping("/{storyId}")
    public ResponseEntity<ApiResponse<Void>> deleteStory(@PathVariable UUID storyId) {
        storyService.deleteStory(storyId);
        return ApiResponse.success(null, "Story deleted successfully.");
    }
}
