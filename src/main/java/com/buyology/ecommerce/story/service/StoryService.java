package com.buyology.ecommerce.story.service;

import com.buyology.ecommerce.common.enums.Language;
import com.buyology.ecommerce.story.domain.Story;
import com.buyology.ecommerce.story.domain.StoryMedia;
import com.buyology.ecommerce.story.domain.StoryTranslation;
import com.buyology.ecommerce.story.dto.CreateStoryRequest;
import com.buyology.ecommerce.story.dto.StoryTranslationRequest;
import com.buyology.ecommerce.story.repository.StoryMediaRepository;
import com.buyology.ecommerce.story.repository.StoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class StoryService {

    private static final String STATIC_STORY_PATH = "static/story";

    private final StoryRepository storyRepository;
    private final StoryMediaRepository storyMediaRepository;

    public StoryService(StoryRepository storyRepository, StoryMediaRepository storyMediaRepository) {
        this.storyRepository = storyRepository;
        this.storyMediaRepository = storyMediaRepository;
    }

    @Transactional
    public Story createStory(CreateStoryRequest request, List<MultipartFile> mediaFiles, UUID createdBy) {
        Story story = new Story();
        story.setCreatedBy(createdBy);
        story.setStatus(request.getStatus());
        story.setStartAt(request.getStartAt());
        story.setEndAt(request.getEndAt());

        // Build translations from request
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

        // Save each media file and create StoryMedia records
        for (int i = 0; i < mediaFiles.size(); i++) {
            MultipartFile file = mediaFiles.get(i);
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String savedFileName = (i + 1) + extension;
            Path filePath = storyDir.resolve(savedFileName);

            try {
                Files.write(filePath, file.getBytes());
            } catch (IOException e) {
                throw new RuntimeException("Failed to save media file: " + originalFilename, e);
            }

            String mediaType = determineMediaType(file.getContentType());
            String url = "/" + STATIC_STORY_PATH + "/" + savedStory.getId() + "/" + savedFileName;

            StoryMedia storyMedia = new StoryMedia(savedStory.getId(), mediaType, url, null, i);
            storyMediaRepository.save(storyMedia);
        }

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
}
