package com.buyology.ecommerce.story.service;

import com.buyology.ecommerce.common.enums.Language;
import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.story.domain.Story;
import com.buyology.ecommerce.story.domain.StoryMedia;
import com.buyology.ecommerce.story.domain.StoryStatus;
import com.buyology.ecommerce.story.domain.StoryTranslation;
import com.buyology.ecommerce.story.dto.CreateStoryRequest;
import com.buyology.ecommerce.story.dto.StoryResponse;
import com.buyology.ecommerce.story.dto.StoryTranslationRequest;
import com.buyology.ecommerce.story.repository.StoryRepository;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.buyology.ecommerce.story.dto.StorySummaryResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class StoryService {

    private static final String STATIC_STORY_PATH = "static/story";

    private final StoryRepository storyRepository;

    public StoryService(StoryRepository storyRepository) {
        this.storyRepository = storyRepository;
    }

    @Transactional
    public Story createStory(CreateStoryRequest request, List<MultipartFile> mediaFiles,
            UUID createdBy) {
        Story story = new Story();
        story.setCreatedBy(createdBy);
        story.setStatus(request.getStatus());

        // Build translations
        List<StoryTranslation> translations = new ArrayList<>();
        for (StoryTranslationRequest tr : request.getTranslations()) {
            translations.addAll(buildTranslations(story, tr));
        }
        story.setTranslations(translations);

        // Save story first to generate the ID
        Story savedStory = storyRepository.save(story);

        // Create the folder: static/story/{storyId}
        Path storyDir = Paths.get(STATIC_STORY_PATH, savedStory.getId().toString());
        try {
            Files.createDirectories(storyDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create story media directory", e);
        }

        // Save each media file with its order
        List<StoryMedia> mediaList = new ArrayList<>();

        for (int i = 0; i < mediaFiles.size(); i++) {
            MultipartFile file = mediaFiles.get(i);
            int orderIndex = i;

            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String savedFileName = orderIndex + extension;
            Path filePath = storyDir.resolve(savedFileName);

            try {
                Files.write(filePath, file.getBytes());
            } catch (IOException e) {
                throw new RuntimeException("Failed to save media file: " + originalFilename, e);
            }

            String mediaType = determineMediaType(file.getContentType());
            String url = "/" + STATIC_STORY_PATH + "/" + savedStory.getId() + "/" + savedFileName;

            StoryMedia storyMedia = new StoryMedia(savedStory.getId(), mediaType, url, null, orderIndex);
            mediaList.add(storyMedia);
        }

        savedStory.setMedia(mediaList);
        storyRepository.save(savedStory);

        return savedStory;
    }

    private List<StoryTranslation> buildTranslations(Story story, StoryTranslationRequest tr) {
        List<StoryTranslation> translations = new ArrayList<>();

        StoryTranslation az = new StoryTranslation();
        az.setStory(story);
        az.setLanguage(Language.AZ);
        az.setTitle(tr.getTitleAz());
        az.setDescription(tr.getDescriptionAz());
        translations.add(az);

        StoryTranslation en = new StoryTranslation();
        en.setStory(story);
        en.setLanguage(Language.EN);
        en.setTitle(tr.getTitleEn());
        en.setDescription(tr.getDescriptionEn());
        translations.add(en);

        StoryTranslation ar = new StoryTranslation();
        ar.setStory(story);
        ar.setLanguage(Language.AR);
        ar.setTitle(tr.getTitleAr());
        ar.setDescription(tr.getDescriptionAr());
        translations.add(ar);

        return translations;
    }

    private String determineMediaType(String contentType) {
        if (contentType != null && contentType.startsWith("video/")) {
            return "VIDEO";
        }
        return "IMAGE";
    }

    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<StorySummaryResponse>>> getStories(Language language) {

        List<StorySummaryResponse> responses = storyRepository.findByStatus(StoryStatus.ACTIVE)
                .stream()
                .map(story -> {
                    StoryTranslation translation = story.getTranslations()
                            .stream()
                            .filter(t -> t.getLanguage() == language)
                            .findFirst()
                            .orElse(null); // fallback if missing

                    StoryMedia thumbnail = story.getMedia()
                            .stream()
                            .filter(m -> m.getUrl() != null && !m.getUrl().isEmpty())
                            .findFirst()
                            .orElse(null);

                    if (translation == null || thumbnail == null)
                        return null;

                    return new StorySummaryResponse(
                            translation.getTitle(),
                            thumbnail.getUrl());
                })
                .filter(r -> r != null)
                .toList();

        return ApiResponse.success(
                responses,
                responses.isEmpty() ? "No stories found." : "Stories fetched successfully");
    }

}
