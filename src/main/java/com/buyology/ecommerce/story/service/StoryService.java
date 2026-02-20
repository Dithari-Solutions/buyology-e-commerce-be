package com.buyology.ecommerce.story.service;

import java.util.List;
import java.util.UUID;
import java.nio.file.Path;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.http.ResponseEntity;
import com.buyology.ecommerce.story.domain.Story;
import com.buyology.ecommerce.common.enums.Language;
import com.buyology.ecommerce.story.domain.StoryMedia;
import com.buyology.ecommerce.story.dto.StoryResponse;
import com.buyology.ecommerce.story.domain.StoryStatus;
import org.springframework.web.multipart.MultipartFile;
import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.story.dto.CreateStoryRequest;
import com.buyology.ecommerce.story.domain.StoryTranslation;
import com.buyology.ecommerce.story.dto.StorySummaryResponse;
import com.buyology.ecommerce.story.repository.StoryRepository;
import com.buyology.ecommerce.story.dto.StoryTranslationRequest;
import org.springframework.transaction.annotation.Transactional;
import com.buyology.ecommerce.story.domain.StoryNotFoundException;

@Service
public class StoryService {

    private static final String STATIC_STORY_PATH = "/opt/uploads/story";

    private final StoryRepository storyRepository;

    public StoryService(StoryRepository storyRepository) {
        this.storyRepository = storyRepository;
    }

    @Transactional
    public Story createStory(CreateStoryRequest request, List<MultipartFile> mediaFiles,
            UUID createdBy) {
        Story story = new Story(createdBy);

        // Build and add translations
        for (StoryTranslationRequest tr : request.getTranslations()) {
            for (StoryTranslation translation : buildTranslations(story, tr)) {
                story.addTranslation(translation);
            }
        }

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
            String url = "/story/" + savedStory.getId() + "/" + savedFileName;

            StoryMedia storyMedia = new StoryMedia(savedStory, mediaType, url, null, orderIndex);
            mediaList.add(storyMedia);
        }

        for (StoryMedia media : mediaList) {
            savedStory.addMedia(media);
        }
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
                            .orElse(null);

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

    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<StoryResponse>> getStoryDetails(Language language, UUID storyId) {
        return storyRepository.findById(storyId)
                .map(story -> {
                    StoryResponse response = StoryResponse.from(story, language);
                    return ApiResponse.success(response, "Story fetched successfully");
                })
                .orElseGet(() -> ApiResponse.failure(HttpStatus.NOT_FOUND, "Story not found."));
    }

    @Transactional
    public void activateStory(UUID storyId) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new StoryNotFoundException(storyId));

        if (story.getStatus() == StoryStatus.ACTIVE) {
            return;
        }

        story.activate();
        storyRepository.save(story);
    }

    @Transactional
    public void deActivateStory(UUID storyId) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new StoryNotFoundException(storyId));

        if (story.getStatus() == StoryStatus.INACTIVE || story.getStatus() == StoryStatus.EXPIRED) {
            return;
        }

        story.deactivate();
    }

    @Transactional
    public void deleteStory(UUID storyId) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new StoryNotFoundException(storyId));

        // Delete media files from disk
        Path storyDir = Paths.get(STATIC_STORY_PATH, storyId.toString());
        if (Files.exists(storyDir)) {
            try (var files = Files.walk(storyDir)) {
                files.sorted(java.util.Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                throw new RuntimeException("Failed to delete file: " + path, e);
                            }
                        });
            } catch (IOException e) {
                throw new RuntimeException("Failed to delete story media directory", e);
            }
        }

        storyRepository.delete(story);
    }

    @Transactional
    public Story addMediaToStory(UUID storyId, List<MultipartFile> mediaFiles) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new StoryNotFoundException(storyId));

        // Determine the next order index based on existing media
        int nextOrderIndex = story.getMedia().stream()
                .mapToInt(StoryMedia::getOrderIndex)
                .max()
                .orElse(-1) + 1;

        Path storyDir = Paths.get(STATIC_STORY_PATH, storyId.toString());
        try {
            Files.createDirectories(storyDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create story media directory", e);
        }

        for (int i = 0; i < mediaFiles.size(); i++) {
            MultipartFile file = mediaFiles.get(i);
            int orderIndex = nextOrderIndex + i;

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
            String url = "/story/" + storyId + "/" + savedFileName;

            StoryMedia storyMedia = new StoryMedia(story, mediaType, url, null, orderIndex);
            story.addMedia(storyMedia);
        }

        return storyRepository.save(story);
    }

}
