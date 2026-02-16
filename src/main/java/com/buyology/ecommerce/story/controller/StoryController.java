package com.buyology.ecommerce.story.controller;

import com.buyology.ecommerce.story.domain.Story;
import com.buyology.ecommerce.story.dto.CreateStoryRequest;
import com.buyology.ecommerce.story.service.StoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.swagger.v3.oas.annotations.Operation;
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
            @RequestPart("request") String requestJson,
            @RequestPart("mediaFiles") List<MultipartFile> mediaFiles,
            @RequestParam("mediaOrders") List<Integer> mediaOrders,
            @RequestHeader("X-User-Id") UUID createdBy) throws Exception {

        CreateStoryRequest request = objectMapper.readValue(requestJson, CreateStoryRequest.class);
        Story story = storyService.createStory(request, mediaFiles, mediaOrders, createdBy);
        return ResponseEntity.ok(story);
    }

}
