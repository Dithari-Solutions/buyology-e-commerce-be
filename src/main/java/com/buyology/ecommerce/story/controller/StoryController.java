package com.buyology.ecommerce.story.controller;

import com.buyology.ecommerce.story.domain.Story;
import com.buyology.ecommerce.story.dto.CreateStoryRequest;
import com.buyology.ecommerce.story.service.StoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

    public StoryController(StoryService storyService) {
        this.storyService = storyService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Story> createStory(
            @RequestPart("story") @Valid CreateStoryRequest request,
            @RequestPart("media") List<MultipartFile> mediaFiles,
            @RequestHeader("X-User-Id") UUID createdBy) {
        Story story = storyService.createStory(request, mediaFiles, createdBy);
        return ResponseEntity.ok(story);
    }
}
