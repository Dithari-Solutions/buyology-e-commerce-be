package com.buyology.ecommerce.story.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Swagger schema helper for the multipart/form-data create-story request.
 * Not used at runtime — only referenced by {@code @Schema(implementation = ...)} in the controller.
 */
@Schema(description = "Multipart form data for creating a story")
public class CreateStoryFormData {

    @Schema(
        description = "Story data as a JSON string. Titles in all 3 languages are required; descriptions are optional.",
        example = "{\"translation\":{\"titleAz\":\"Yeni Hekayə\",\"titleEn\":\"New Story\",\"titleAr\":\"قصة جديدة\",\"descriptionAz\":\"Azerbaijani təsvir\",\"descriptionEn\":\"English description\",\"descriptionAr\":\"الوصف بالعربية\"},\"status\":\"ACTIVE\"}"
    )
    private String request;

    @Schema(
        description = "Thumbnail image file (separate from media)",
        type = "string",
        format = "binary"
    )
    private MultipartFile thumbnail;

    @ArraySchema(schema = @Schema(
        description = "Story media files (images or videos)",
        type = "string",
        format = "binary"
    ))
    private List<MultipartFile> mediaFiles;

    public String getRequest() { return request; }
    public MultipartFile getThumbnail() { return thumbnail; }
    public List<MultipartFile> getMediaFiles() { return mediaFiles; }
}
