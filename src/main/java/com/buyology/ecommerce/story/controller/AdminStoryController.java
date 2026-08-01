package com.buyology.ecommerce.story.controller;

import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Encoding;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.buyology.ecommerce.common.enums.Language;
import com.buyology.ecommerce.story.dto.CreateStoryFormData;
import com.buyology.ecommerce.story.dto.StoryResponse;
import org.springframework.web.multipart.MultipartFile;
import com.buyology.ecommerce.story.service.StoryService;
import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.common.utils.SecurityUtils;
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
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('story:read') or @rbacPolicy.legacyAdmin()")
    @GetMapping
    public ResponseEntity<ApiResponse<List<StorySummaryResponse>>> getStories(
            @RequestParam Language language) {
        return storyService.getAdminStories(language);
    }

    @Operation(summary = "Get story details with status")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('story:read') or @rbacPolicy.legacyAdmin()")
    @GetMapping("/{storyId}")
    public ResponseEntity<ApiResponse<StoryResponse>> getStoryDetails(
            @RequestParam Language language,
            @PathVariable UUID storyId) {
        return storyService.getAdminStoryDetails(language, storyId);
    }

    @Operation(summary = "Create a new story")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        required = true,
        content = @Content(
            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
            schema = @Schema(implementation = CreateStoryFormData.class),
            encoding = @Encoding(name = "request", contentType = MediaType.APPLICATION_JSON_VALUE)
        )
    )
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('story:create') or @rbacPolicy.legacyAdmin()")
    @PostMapping(value = "/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<StoryResponse>> createStory(
            @org.springframework.web.bind.annotation.RequestPart("request") String requestJson,
            @org.springframework.web.bind.annotation.RequestPart("thumbnail") MultipartFile thumbnail,
            @org.springframework.web.bind.annotation.RequestPart(value = "mediaFiles", required = false) List<MultipartFile> mediaFiles) throws Exception {

        UUID createdBy = SecurityUtils.currentUserIdOrNull();
        CreateStoryRequest request = objectMapper.readValue(requestJson, CreateStoryRequest.class);
        StoryResponse response = StoryResponse.from(
                storyService.createStory(request, thumbnail, mediaFiles, createdBy), Language.AZ);
        return ApiResponse.created(response, "Story created successfully");
    }

    @Operation(summary = "Get a presigned URL to upload a story media file directly to storage")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('story:create') or @rbacPolicy.legacyAdmin()")
    @PostMapping("/presign-upload")
    public ResponseEntity<ApiResponse<com.buyology.ecommerce.story.dto.PresignUploadResponse>> presignUpload(
            @jakarta.validation.Valid @RequestBody com.buyology.ecommerce.story.dto.PresignUploadRequest request) {
        return ApiResponse.success(storyService.presignUpload(request), "Upload URL generated");
    }

    @Operation(summary = "Create a story from already-uploaded media keys (direct-to-storage upload)")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('story:create') or @rbacPolicy.legacyAdmin()")
    @PostMapping("/create-direct")
    public ResponseEntity<ApiResponse<StoryResponse>> createStoryDirect(
            @jakarta.validation.Valid @RequestBody com.buyology.ecommerce.story.dto.CreateStoryWithKeysRequest request) {
        UUID createdBy = SecurityUtils.currentUserIdOrNull();
        StoryResponse response = StoryResponse.from(
                storyService.createStoryFromKeys(request, createdBy), Language.AZ);
        return ApiResponse.created(response, "Story created successfully");
    }

    @Operation(summary = "Add media files to an existing story")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('story:update') or @rbacPolicy.legacyAdmin()")
    @PostMapping(value = "/{storyId}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Void>> addMediaToStory(
            @PathVariable UUID storyId,
            @RequestPart("mediaFiles") List<MultipartFile> mediaFiles) {
        storyService.addMediaToStory(storyId, mediaFiles);
        return ApiResponse.success(null, "Media added to story successfully.");
    }

    @Operation(summary = "Activate a story")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('story:moderate') or @rbacPolicy.legacyAdmin()")
    @PostMapping("/{storyId}/activate")
    public ResponseEntity<ApiResponse<Void>> activateStory(@PathVariable UUID storyId) {
        storyService.activateStory(storyId);
        return ApiResponse.success(null, "Story activated successfully.");
    }

    @Operation(summary = "Deactivate a story")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('story:moderate') or @rbacPolicy.legacyAdmin()")
    @PostMapping("/{storyId}/deactivate")
    public ResponseEntity<ApiResponse<Void>> deActivateStory(@PathVariable UUID storyId) {
        storyService.deActivateStory(storyId);
        return ApiResponse.success(null, "Story deactivated successfully.");
    }

    @Operation(summary = "Set the display order of a story (lower is shown first)")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('story:update') or @rbacPolicy.legacyAdmin()")
    @PatchMapping("/{storyId}/display-order")
    public ResponseEntity<ApiResponse<Void>> setDisplayOrder(
            @PathVariable UUID storyId,
            @RequestParam int order) {
        storyService.updateDisplayOrder(storyId, order);
        return ApiResponse.success(null, "Display order updated.");
    }

    @Operation(summary = "Delete a story with all its translations and media")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('story:delete') or @rbacPolicy.legacyAdmin()")
    @DeleteMapping("/{storyId}")
    public ResponseEntity<ApiResponse<Void>> deleteStory(@PathVariable UUID storyId) {
        storyService.deleteStory(storyId);
        return ApiResponse.success(null, "Story deleted successfully.");
    }

    @Operation(summary = "Delete a specific media file from a story")
    @PreAuthorize("hasRole('SUPERADMIN') or hasAuthority('story:delete') or @rbacPolicy.legacyAdmin()")
    @DeleteMapping("/{storyId}/media/{mediaId}")
    public ResponseEntity<ApiResponse<Void>> deleteMedia(
            @PathVariable UUID storyId,
            @PathVariable UUID mediaId) {
        storyService.deleteMediaFromStory(storyId, mediaId);
        return ApiResponse.success(null, "Media deleted from story successfully.");
    }
}
