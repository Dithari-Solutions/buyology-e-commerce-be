package com.buyology.ecommerce.story.service;

import java.util.List;
import java.util.UUID;
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
import java.util.Comparator;
import com.buyology.ecommerce.story.dto.StorySummaryResponse;
import com.buyology.ecommerce.story.repository.StoryRepository;
import com.buyology.ecommerce.story.dto.StoryTranslationRequest;
import org.springframework.transaction.annotation.Transactional;
import com.buyology.ecommerce.story.domain.StoryNotFoundException;
import com.buyology.ecommerce.infrastructure.external.ContaboObjectService;

@Service
public class StoryService {

    private final StoryRepository storyRepository;
    private final com.buyology.ecommerce.story.repository.StoryMediaRepository storyMediaRepository;
    private final ContaboObjectService contaboObjectService;

    public StoryService(StoryRepository storyRepository, 
                        com.buyology.ecommerce.story.repository.StoryMediaRepository storyMediaRepository,
                        ContaboObjectService contaboObjectService) {
        this.storyRepository = storyRepository;
        this.storyMediaRepository = storyMediaRepository;
        this.contaboObjectService = contaboObjectService;
    }

    @Transactional
    public void deleteMediaFromStory(UUID storyId, UUID mediaId) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new StoryNotFoundException(storyId));

        StoryMedia media = storyMediaRepository.findById(mediaId)
                .orElseThrow(() -> new IllegalArgumentException("Media not found with id: " + mediaId));

        if (!media.getStory().getId().equals(storyId)) {
            throw new IllegalArgumentException("Media does not belong to the specified story");
        }

        // Delete from S3
        contaboObjectService.deleteFile(media.getUrl());

        // Remove from list and delete record
        story.removeMedia(media);
        storyMediaRepository.delete(media);
        storyRepository.save(story);
    }

    @Transactional
    public Story createStory(CreateStoryRequest request, MultipartFile thumbnail,
            List<MultipartFile> mediaFiles, UUID createdBy) {
        Story story = new Story(createdBy);

        // Build and add translations from single translation object
        for (StoryTranslation translation : buildTranslations(story, request.getTranslation())) {
            story.addTranslation(translation);
        }

        // Save story first to generate the ID
        Story savedStory = storyRepository.save(story);

        // Save thumbnail to Contabo S3
        if (thumbnail != null && !thumbnail.isEmpty()) {
            String thumbFilename = thumbnail.getOriginalFilename();
            String thumbExt = (thumbFilename != null && thumbFilename.contains("."))
                    ? thumbFilename.substring(thumbFilename.lastIndexOf("."))
                    : "";
            String savedThumbName = "thumbnail" + thumbExt;
            String key = "stories/" + savedStory.getId() + "/" + savedThumbName;
            
            String s3Key = contaboObjectService.uploadFile(key, thumbnail);
            savedStory.setThumbnailUrl(s3Key);
        }

        // Save each media file to Contabo S3
        List<StoryMedia> mediaList = new ArrayList<>();

        if (mediaFiles != null) {
            for (int i = 0; i < mediaFiles.size(); i++) {
                MultipartFile file = mediaFiles.get(i);
                int orderIndex = i;

                String originalFilename = file.getOriginalFilename();
                String extension = "";
                if (originalFilename != null && originalFilename.contains(".")) {
                    extension = originalFilename.substring(originalFilename.lastIndexOf("."));
                }
                String savedFileName = orderIndex + extension;
                String key = "stories/" + savedStory.getId() + "/" + savedFileName;

                String s3Key = contaboObjectService.uploadFile(key, file);

                String mediaType = determineMediaType(file.getContentType());
                StoryMedia storyMedia = new StoryMedia(savedStory, mediaType, s3Key, null, orderIndex);
                mediaList.add(storyMedia);
            }
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
    public ResponseEntity<ApiResponse<List<StorySummaryResponse>>> getPublicStories(Language language) {
        List<StorySummaryResponse> responses = storyRepository.findByStatus(StoryStatus.ACTIVE)
                .stream()
                .map(story -> toSummaryResponse(story, language))
                .filter(r -> r != null)
                .toList();

        return ApiResponse.success(
                responses,
                responses.isEmpty() ? "No stories found." : "Stories fetched successfully");
    }

    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<StoryResponse>> getPublicStoryDetails(Language language, UUID storyId) {
        return storyRepository.findById(storyId)
                .filter(story -> story.getStatus() == StoryStatus.ACTIVE)
                .map(story -> ApiResponse.success(mapToResponse(story, language), "Story fetched successfully"))
                .orElseGet(() -> ApiResponse.failure(HttpStatus.NOT_FOUND, "Story not found."));
    }

    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<StorySummaryResponse>>> getAdminStories(Language language) {
        List<StorySummaryResponse> responses = storyRepository.findAll()
                .stream()
                .map(story -> toSummaryResponse(story, language))
                .filter(r -> r != null)
                .toList();

        return ApiResponse.success(
                responses,
                responses.isEmpty() ? "No stories found." : "Stories fetched successfully");
    }

    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<StoryResponse>> getAdminStoryDetails(Language language, UUID storyId) {
        return storyRepository.findById(storyId)
                .map(story -> ApiResponse.success(mapToResponse(story, language), "Story fetched successfully"))
                .orElseGet(() -> ApiResponse.failure(HttpStatus.NOT_FOUND, "Story not found."));
    }

    private StorySummaryResponse toSummaryResponse(Story story, Language language) {
        StoryTranslation translation = story.getTranslations()
                .stream()
                .filter(t -> t.getLanguage() == language)
                .findFirst()
                .orElse(null);

        if (translation == null)
            return null;

        List<StoryMedia> sortedMedia = story.getMedia()
                .stream()
                .filter(m -> m.getUrl() != null && !m.getUrl().isEmpty())
                .sorted(Comparator.comparingInt(StoryMedia::getOrderIndex))
                .toList();

        List<StoryResponse.MediaItem> mediaItems = sortedMedia.stream()
                .map(this::toMediaItemDto)
                .toList();

        return new StorySummaryResponse(
                story.getId(),
                translation.getTitle(),
                contaboObjectService.getPresignedUrl(story.getThumbnailUrl()),
                story.getStatus().name(),
                mediaItems);
    }

    private StoryResponse mapToResponse(Story story, Language language) {
        StoryResponse response = new StoryResponse();
        response.setId(story.getId());
        response.setThumbnailUrl(contaboObjectService.getPresignedUrl(story.getThumbnailUrl()));
        response.setStatus(story.getStatus().name());
        response.setCreatedAt(story.getCreatedAt());

        if (story.getTranslations() != null) {
            story.getTranslations().stream()
                    .filter(t -> t.getLanguage() == language)
                    .findFirst()
                    .ifPresent(t -> {
                        response.setTitle(t.getTitle());
                        response.setDescription(t.getDescription());
                    });
        }

        if (story.getMedia() != null) {
            response.setMedia(story.getMedia().stream()
                    .sorted(Comparator.comparingInt(StoryMedia::getOrderIndex))
                    .map(this::toMediaItemDto)
                    .toList());
        }

        return response;
    }

    private StoryResponse.MediaItem toMediaItemDto(StoryMedia m) {
        StoryResponse.MediaItem item = new StoryResponse.MediaItem();
        item.setMediaType(m.getMediaType());
        item.setUrl(contaboObjectService.getPresignedUrl(m.getUrl()));
        item.setThumbnailUrl(contaboObjectService.getPresignedUrl(m.getThumbnailUrl()));
        item.setOrderIndex(m.getOrderIndex());
        return item;
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
        storyRepository.save(story);
    }

    @Transactional
    public void deleteStory(UUID storyId) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new StoryNotFoundException(storyId));

        // Delete media files from Contabo S3
        contaboObjectService.deleteFolder("stories/" + storyId);

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

        for (int i = 0; i < mediaFiles.size(); i++) {
            MultipartFile file = mediaFiles.get(i);
            int orderIndex = nextOrderIndex + i;

            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String savedFileName = orderIndex + extension;
            String key = "stories/" + storyId + "/" + savedFileName;

            String s3Key = contaboObjectService.uploadFile(key, file);

            String mediaType = determineMediaType(file.getContentType());
            StoryMedia storyMedia = new StoryMedia(story, mediaType, s3Key, null, orderIndex);
            story.addMedia(storyMedia);
        }

        return storyRepository.save(story);
    }

}
