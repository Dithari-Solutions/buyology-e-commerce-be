package com.buyology.ecommerce.story.dto;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request a presigned URL to upload a story media file directly to storage")
public class PresignUploadRequest {

    @NotBlank(message = "filename is required")
    @Schema(description = "Original file name, used to derive the file extension", example = "promo.mp4")
    private String filename;

    @NotBlank(message = "contentType is required")
    @Schema(description = "MIME type of the file", example = "video/mp4")
    private String contentType;

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }
}
