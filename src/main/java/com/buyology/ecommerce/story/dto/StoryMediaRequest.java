package com.buyology.ecommerce.story.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

public class StoryMediaRequest {

    @NotNull(message = "Media file is required")
    private MultipartFile file;

    @Min(value = 0, message = "Order index must be non-negative")
    private int orderIndex;

    // ========================
    // Getters & Setters
    // ========================

    public MultipartFile getFile() {
        return file;
    }

    public void setFile(MultipartFile file) {
        this.file = file;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }
}
