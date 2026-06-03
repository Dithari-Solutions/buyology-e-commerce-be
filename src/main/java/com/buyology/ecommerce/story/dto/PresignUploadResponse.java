package com.buyology.ecommerce.story.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Presigned upload URL and the storage key the file will live at")
public record PresignUploadResponse(
        @Schema(description = "Presigned PUT URL the client uploads the raw file to") String uploadUrl,
        @Schema(description = "Storage key to send back when creating the story") String key) {
}
