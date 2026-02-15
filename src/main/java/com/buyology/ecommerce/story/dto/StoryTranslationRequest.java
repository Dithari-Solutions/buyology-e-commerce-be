package com.buyology.ecommerce.story.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class StoryTranslationRequest {

    @NotBlank(message = "Azerbaijani title is required")
    @Size(max = 255, message = "Azerbaijani title must not exceed 255 characters")
    private String titleAz;

    @NotBlank(message = "English title is required")
    @Size(max = 255, message = "English title must not exceed 255 characters")
    private String titleEn;

    @NotBlank(message = "Arabic title is required")
    @Size(max = 255, message = "Arabic title must not exceed 255 characters")
    private String titleAr;

    private String descriptionAz;

    private String descriptionEn;

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
