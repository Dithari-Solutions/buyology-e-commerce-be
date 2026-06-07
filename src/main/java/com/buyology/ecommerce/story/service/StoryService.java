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
import com.buyology.ecommerce.story.dto.StoryLikeResponse;
import com.buyology.ecommerce.story.dto.StoryViewResponse;
import com.buyology.ecommerce.story.repository.StoryLikeRepository;
import com.buyology.ecommerce.story.repository.StoryViewRepository;
import com.buyology.ecommerce.common.utils.FileValidationUtils;
import com.buyology.ecommerce.infrastructure.external.ContaboObjectService;

@Service
public class StoryService {

    /** Prefix under which direct uploads are staged before a story is created. */
    private static final String STAGING_PREFIX = "stories/uploads/";
    /** How long a presigned upload URL stays valid. */
    private static final java.time.Duration UPLOAD_URL_TTL = java.time.Duration.ofMinutes(30);

    private final StoryRepository storyRepository;
    private final com.buyology.ecommerce.story.repository.StoryMediaRepository storyMediaRepository;
    private final StoryViewRepository storyViewRepository;
    private final StoryLikeRepository storyLikeRepository;
    private final StoryEngagementWriter engagementWriter;
    private final ContaboObjectService contaboObjectService;

    public StoryService(StoryRepository storyRepository,
                        com.buyology.ecommerce.story.repository.StoryMediaRepository storyMediaRepository,
                        StoryViewRepository storyViewRepository,
                        StoryLikeRepository storyLikeRepository,
                        StoryEngagementWriter engagementWriter,
                        ContaboObjectService contaboObjectService) {
        this.storyRepository = storyRepository;
        this.storyMediaRepository = storyMediaRepository;
        this.storyViewRepository = storyViewRepository;
        this.storyLikeRepository = storyLikeRepository;
        this.engagementWriter = engagementWriter;
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
        FileValidationUtils.validateImage(thumbnail);
        FileValidationUtils.validateMediaList(mediaFiles);

        Story story = new Story(createdBy);
        if (request.getDisplayOrder() != null) {
            story.setDisplayOrder(request.getDisplayOrder());
        }

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

    /**
     * Issues a presigned URL so the client can upload a single story media file
     * (image or video) directly to storage, bypassing the app server and edge
     * body-size limits. The file is staged under {@code stories/uploads/} and
     * moved into its final location when the story is created.
     */
    public com.buyology.ecommerce.story.dto.PresignUploadResponse presignUpload(
            com.buyology.ecommerce.story.dto.PresignUploadRequest request) {
        if (!FileValidationUtils.isAllowedMediaContentType(request.getContentType())) {
            throw new IllegalArgumentException("Unsupported content type: " + request.getContentType());
        }
        String ext = extractExtension(request.getFilename());
        String key = STAGING_PREFIX + UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);
        String uploadUrl = contaboObjectService.generatePresignedUploadUrl(key, request.getContentType(), UPLOAD_URL_TTL);
        return new com.buyology.ecommerce.story.dto.PresignUploadResponse(uploadUrl, key);
    }

    /**
     * Creates a story from media that was already uploaded directly to storage
     * via {@link #presignUpload}. Staged objects are copied (server-side) into
     * {@code stories/{storyId}/...} so the existing delete-by-folder cleanup
     * keeps working, then the staged copies are removed.
     */
    @Transactional
    public Story createStoryFromKeys(com.buyology.ecommerce.story.dto.CreateStoryWithKeysRequest request, UUID createdBy) {
        validateStagingKey(request.getThumbnailKey());

        Story story = new Story(createdBy);
        if (request.getDisplayOrder() != null) {
            story.setDisplayOrder(request.getDisplayOrder());
        }
        for (StoryTranslation translation : buildTranslations(story, request.getTranslation())) {
            story.addTranslation(translation);
        }
        Story savedStory = storyRepository.save(story);

        // Thumbnail: move staged upload into the story folder
        String thumbExt = extractExtension(request.getThumbnailKey());
        String thumbFinalKey = "stories/" + savedStory.getId() + "/thumbnail" + (thumbExt.isEmpty() ? "" : "." + thumbExt);
        contaboObjectService.copyObject(request.getThumbnailKey(), thumbFinalKey);
        contaboObjectService.deleteFile(request.getThumbnailKey());
        savedStory.setThumbnailUrl(thumbFinalKey);

        // Media: copy each staged upload into the story folder, re-indexed 0..n-1
        List<com.buyology.ecommerce.story.dto.CreateStoryWithKeysRequest.MediaItem> items =
                request.getMedia() == null ? new ArrayList<>() : new ArrayList<>(request.getMedia());
        items.sort(Comparator.comparingInt(com.buyology.ecommerce.story.dto.CreateStoryWithKeysRequest.MediaItem::getOrderIndex));

        for (int i = 0; i < items.size(); i++) {
            com.buyology.ecommerce.story.dto.CreateStoryWithKeysRequest.MediaItem item = items.get(i);
            validateStagingKey(item.getKey());
            String ext = extractExtension(item.getKey());
            String finalKey = "stories/" + savedStory.getId() + "/" + i + (ext.isEmpty() ? "" : "." + ext);
            contaboObjectService.copyObject(item.getKey(), finalKey);
            contaboObjectService.deleteFile(item.getKey());

            String mediaType = determineMediaType(item.getContentType());
            savedStory.addMedia(new StoryMedia(savedStory, mediaType, finalKey, null, i));
        }

        storyRepository.save(savedStory);
        return savedStory;
    }

    private void validateStagingKey(String key) {
        if (key == null || key.isBlank() || !key.startsWith(STAGING_PREFIX) || key.contains("..")) {
            throw new IllegalArgumentException("Invalid upload key: " + key);
        }
    }

    /** Returns the lowercase, sanitized file extension (without the dot), or "" if none. */
    private String extractExtension(String nameOrKey) {
        if (nameOrKey == null) {
            return "";
        }
        int dot = nameOrKey.lastIndexOf('.');
        if (dot < 0 || dot == nameOrKey.length() - 1) {
            return "";
        }
        return nameOrKey.substring(dot + 1).toLowerCase().replaceAll("[^a-z0-9]", "");
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
    public ResponseEntity<ApiResponse<List<StorySummaryResponse>>> getPublicStories(Language language, UUID currentUserId) {
        List<StorySummaryResponse> responses = storyRepository
                .findByStatusOrderByDisplayOrderAscCreatedAtDesc(StoryStatus.ACTIVE)
                .stream()
                .map(story -> toSummaryResponse(story, language, currentUserId))
                .filter(r -> r != null)
                .toList();

        return ApiResponse.success(
                responses,
                responses.isEmpty() ? "No stories found." : "Stories fetched successfully");
    }

    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<StoryResponse>> getPublicStoryDetails(Language language, UUID storyId, UUID currentUserId) {
        return storyRepository.findById(storyId)
                .filter(story -> story.getStatus() == StoryStatus.ACTIVE)
                .map(story -> ApiResponse.success(mapToResponse(story, language, currentUserId), "Story fetched successfully"))
                .orElseGet(() -> ApiResponse.failure(HttpStatus.NOT_FOUND, "Story not found."));
    }

    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<StorySummaryResponse>>> getAdminStories(Language language) {
        List<StorySummaryResponse> responses = storyRepository
                .findAllByOrderByDisplayOrderAscCreatedAtDesc()
                .stream()
                .map(story -> toSummaryResponse(story, language, null))
                .filter(r -> r != null)
                .toList();

        return ApiResponse.success(
                responses,
                responses.isEmpty() ? "No stories found." : "Stories fetched successfully");
    }

    @Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<StoryResponse>> getAdminStoryDetails(Language language, UUID storyId) {
        return storyRepository.findById(storyId)
                .map(story -> ApiResponse.success(mapToResponse(story, language, null), "Story fetched successfully"))
                .orElseGet(() -> ApiResponse.failure(HttpStatus.NOT_FOUND, "Story not found."));
    }

    public ResponseEntity<ApiResponse<StoryViewResponse>> recordView(UUID storyId, UUID userId, String viewerHash) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new StoryNotFoundException(storyId));
        if (story.getStatus() != StoryStatus.ACTIVE) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Story not found.");
        }

        if (userId != null) {
            if (!storyViewRepository.existsByStoryIdAndUserId(storyId, userId)) {
                engagementWriter.insertViewIfAbsent(storyId, userId, null);
            }
        } else if (viewerHash != null && !viewerHash.isBlank()) {
            if (!storyViewRepository.existsByStoryIdAndViewerHash(storyId, viewerHash)) {
                engagementWriter.insertViewIfAbsent(storyId, null, viewerHash);
            }
        } else {
            return ApiResponse.failure(HttpStatus.BAD_REQUEST, "Cannot identify viewer");
        }
        long count = storyViewRepository.countByStoryId(storyId);
        return ApiResponse.success(new StoryViewResponse(count), "View recorded");
    }

    public ResponseEntity<ApiResponse<StoryLikeResponse>> likeStory(UUID storyId, UUID userId) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new StoryNotFoundException(storyId));
        if (story.getStatus() != StoryStatus.ACTIVE) {
            return ApiResponse.failure(HttpStatus.NOT_FOUND, "Story not found.");
        }
        if (!storyLikeRepository.existsByStoryIdAndUserId(storyId, userId)) {
            engagementWriter.insertLikeIfAbsent(storyId, userId);
        }
        long count = storyLikeRepository.countByStoryId(storyId);
        return ApiResponse.success(new StoryLikeResponse(true, count), "Liked");
    }

    @Transactional
    public ResponseEntity<ApiResponse<StoryLikeResponse>> unlikeStory(UUID storyId, UUID userId) {
        if (!storyRepository.existsById(storyId)) {
            throw new StoryNotFoundException(storyId);
        }
        storyLikeRepository.deleteByStoryIdAndUserId(storyId, userId);
        long count = storyLikeRepository.countByStoryId(storyId);
        return ApiResponse.success(new StoryLikeResponse(false, count), "Unliked");
    }

    private StorySummaryResponse toSummaryResponse(Story story, Language language, UUID currentUserId) {
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

        long viewCount = storyViewRepository.countByStoryId(story.getId());
        long likeCount = storyLikeRepository.countByStoryId(story.getId());
        boolean likedByMe = currentUserId != null
                && storyLikeRepository.existsByStoryIdAndUserId(story.getId(), currentUserId);

        return new StorySummaryResponse(
                story.getId(),
                translation.getTitle(),
                contaboObjectService.getPresignedUrl(story.getThumbnailUrl()),
                story.getStatus().name(),
                mediaItems,
                viewCount,
                likeCount,
                likedByMe,
                story.getDisplayOrder());
    }

    private StoryResponse mapToResponse(Story story, Language language, UUID currentUserId) {
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

        response.setViewCount(storyViewRepository.countByStoryId(story.getId()));
        response.setLikeCount(storyLikeRepository.countByStoryId(story.getId()));
        response.setLikedByMe(currentUserId != null
                && storyLikeRepository.existsByStoryIdAndUserId(story.getId(), currentUserId));

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
    public void updateDisplayOrder(UUID storyId, int order) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new StoryNotFoundException(storyId));
        story.setDisplayOrder(order);
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
        FileValidationUtils.validateMediaList(mediaFiles);
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
