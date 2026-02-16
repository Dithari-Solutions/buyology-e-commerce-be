package com.buyology.ecommerce.story.controller;

import com.buyology.ecommerce.common.enums.Language;
import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.common.utils.LangUtil;
import com.buyology.ecommerce.story.domain.Story;
import com.buyology.ecommerce.story.dto.CreateStoryRequest;
import com.buyology.ecommerce.story.dto.StoryResponse;
import com.buyology.ecommerce.story.dto.StorySummaryResponse;
import com.buyology.ecommerce.story.service.StoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

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
            @RequestPart("request") @io.swagger.v3.oas.annotations.media.Schema(
                    type = "string",
                    format = "json",
                    description = "Story creation request as JSON",
                    example = "{\"translations\":[{\"titleAz\":\"Yeni Hekayə\",\"titleEn\":\"New Story\",\"titleAr\":\"قصة جديدة\",\"descriptionAz\":\"Bu yeni hekayənin təsviridir\",\"descriptionEn\":\"This is the description of the new story\",\"descriptionAr\":\"هذا وصف القصة الجديدة\"}],\"status\":\"ACTIVE\"}"
            ) String requestJson,
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

}
