package com.buyology.ecommerce.story.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Translation data for a story in Azerbaijani, English, and Arabic")
public class StoryTranslationRequest {

    @NotBlank(message = "Azerbaijani title is required")
    @Size(max = 255, message = "Azerbaijani title must not exceed 255 characters")
    @Schema(description = "Title in Azerbaijani", example = "Yeni Hekayə")
    private String titleAz;

    @NotBlank(message = "English title is required")
    @Size(max = 255, message = "English title must not exceed 255 characters")
    @Schema(description = "Title in English", example = "New Story")
    private String titleEn;

    @NotBlank(message = "Arabic title is required")
    @Size(max = 255, message = "Arabic title must not exceed 255 characters")
    @Schema(description = "Title in Arabic", example = "قصة جديدة")
    private String titleAr;

    @Schema(description = "Description in Azerbaijani", example = "Bu yeni hekayənin təsviridir")
    private String descriptionAz;

    @Schema(description = "Description in English", example = "This is the description of the new story")
    private String descriptionEn;

    @Schema(description = "Description in Arabic", example = "هذا وصف القصة الجديدة")
    private String descriptionAr;

    // ========================
    // Getters & Setters
    // ========================

    public String getTitleAz() {
        return titleAz;
    }

    public void setTitleAz(String titleAz) {
        this.titleAz = titleAz;
    }

    public String getTitleEn() {
        return titleEn;
    }

    public void setTitleEn(String titleEn) {
        this.titleEn = titleEn;
    }

    public String getTitleAr() {
        return titleAr;
    }

    public void setTitleAr(String titleAr) {
        this.titleAr = titleAr;
    }

    public String getDescriptionAz() {
        return descriptionAz;
    }

    public void setDescriptionAz(String descriptionAz) {
        this.descriptionAz = descriptionAz;
    }

    public String getDescriptionEn() {
        return descriptionEn;
    }

    public void setDescriptionEn(String descriptionEn) {
        this.descriptionEn = descriptionEn;
    }

    public String getDescriptionAr() {
        return descriptionAr;
    }

    public void setDescriptionAr(String descriptionAr) {
        this.descriptionAr = descriptionAr;
    }
}
