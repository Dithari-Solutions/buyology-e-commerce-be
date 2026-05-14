package com.buyology.ecommerce.banner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.multipart.MultipartFile;

@Schema(description = "Multipart form-data for creating a banner")
public class CreateBannerFormData {

    @Schema(implementation = CreateBannerRequest.class)
    public CreateBannerRequest request;

    @Schema(type = "string", format = "binary", description = "Background image file")
    public MultipartFile background;
}
