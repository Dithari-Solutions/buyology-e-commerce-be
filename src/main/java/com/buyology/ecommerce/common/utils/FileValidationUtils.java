package com.buyology.ecommerce.common.utils;

import com.buyology.ecommerce.common.exception.FileValidationException;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Set;

public class FileValidationUtils {

    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final Set<String> ALLOWED_IMAGE_MIME_TYPES = Set.of("image/jpeg", "image/png", "image/gif", "image/webp");
    
    private static final Set<String> ALLOWED_VIDEO_EXTENSIONS = Set.of("mp4", "mov", "avi", "mkv", "webm");
    private static final Set<String> ALLOWED_VIDEO_MIME_TYPES = Set.of("video/mp4", "video/quicktime", "video/x-msvideo", "video/x-matroska", "video/webm");

    private static final Set<String> SUPPLIER_PRODUCT_IMAGE_EXTENSIONS = Set.of("png", "webp");
    private static final Set<String> SUPPLIER_PRODUCT_IMAGE_MIME_TYPES = Set.of("image/png", "image/webp");
    private static final long SUPPLIER_PRODUCT_MAX_BYTES = 5L * 1024 * 1024; // 5 MB
    private static final int SUPPLIER_PRODUCT_MAX_IMAGES = 8;
    private static final double MIN_TRANSPARENT_PIXEL_RATIO = 0.01; // 1%

    private static final List<String> MALICIOUS_SIGNATURES = List.of(
            "<?php",
            "<?PHP",
            "<script",
            "<SCRIPT",
            "eval(",
            "exec(",
            "system(",
            "passthru(",
            "base64_decode(",
            "$_GET",
            "$_POST",
            "$_REQUEST",
            "python",
            "perl",
            "bash",
            "sh\n",
            "cmd.exe",
            "/bin/sh",
            "/bin/bash"
    );

    /**
     * Validates that the file is a valid image and does not contain malicious scripts.
     */
    public static void validateImage(MultipartFile file) {
        validate(file, false);
    }

    /**
     * Validates that the file is a valid image or video and does not contain malicious scripts.
     */
    public static void validateMedia(MultipartFile file) {
        validate(file, true);
    }

    private static void validate(MultipartFile file, boolean allowVideo) {
        if (file == null || file.isEmpty()) {
            return;
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new FileValidationException("File must have an extension");
        }
        String extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        String contentType = file.getContentType();

        boolean isImage = ALLOWED_IMAGE_EXTENSIONS.contains(extension) && 
                         (contentType != null && ALLOWED_IMAGE_MIME_TYPES.contains(contentType));
        
        boolean isVideo = allowVideo && ALLOWED_VIDEO_EXTENSIONS.contains(extension) && 
                         (contentType != null && ALLOWED_VIDEO_MIME_TYPES.contains(contentType));

        if (!isImage && !isVideo) {
            String allowed = allowVideo ? "images or videos" : "images";
            throw new FileValidationException("Invalid file type. Only " + allowed + " are allowed.");
        }

        // Scan for malicious content (scripts)
        scanForMaliciousContent(file);

        // Verify Integrity for images
        if (isImage && !extension.equals("webp")) {
            verifyImageIntegrity(file);
        }
    }

    public static void validateImages(List<MultipartFile> files) {
        if (files == null) return;
        for (MultipartFile file : files) validateImage(file);
    }

    public static void validateMediaList(List<MultipartFile> files) {
        if (files == null) return;
        for (MultipartFile file : files) validateMedia(file);
    }

    /**
     * Stricter validation for supplier-uploaded product images.
     * - PNG or WebP only (transparency required for the bg-removed product photos)
     * - Max 5 MB per image, max 8 images
     * - PNGs must have at least ~1% fully transparent pixels (heuristic: rejects flat-bg photos)
     */
    public static void validateSupplierProductImages(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) return;
        if (files.size() > SUPPLIER_PRODUCT_MAX_IMAGES) {
            throw new FileValidationException(
                    "Too many images. Max " + SUPPLIER_PRODUCT_MAX_IMAGES + " allowed.");
        }
        for (MultipartFile file : files) {
            validateSupplierProductImage(file);
        }
    }

    public static void validateSupplierProductImage(MultipartFile file) {
        if (file == null || file.isEmpty()) return;

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new FileValidationException("File must have an extension");
        }
        String extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        String contentType = file.getContentType();

        if (!SUPPLIER_PRODUCT_IMAGE_EXTENSIONS.contains(extension)
                || contentType == null
                || !SUPPLIER_PRODUCT_IMAGE_MIME_TYPES.contains(contentType)) {
            throw new FileValidationException(
                    "Product images must be PNG or WebP with a transparent background.");
        }

        if (file.getSize() > SUPPLIER_PRODUCT_MAX_BYTES) {
            throw new FileValidationException(
                    "Image exceeds 5 MB limit (" + (file.getSize() / 1024 / 1024) + " MB).");
        }

        scanForMaliciousContent(file);

        if ("png".equals(extension)) {
            verifyHasTransparency(file);
        }
        // WebP transparency is not reliably detectable via stock ImageIO; trust the MIME/ext.
    }

    private static void verifyHasTransparency(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            BufferedImage image = ImageIO.read(is);
            if (image == null) {
                throw new FileValidationException("Invalid image file or corrupted content");
            }
            if (!image.getColorModel().hasAlpha()) {
                throw new FileValidationException(
                        "PNG must have an alpha channel (background-removed image expected).");
            }
            int width = image.getWidth();
            int height = image.getHeight();
            int sampleStep = Math.max(1, (width * height) / 10000);
            int transparent = 0;
            int sampled = 0;
            for (int y = 0; y < height; y += Math.max(1, sampleStep / Math.max(1, width))) {
                for (int x = 0; x < width; x += Math.max(1, sampleStep)) {
                    int argb = image.getRGB(x, y);
                    int alpha = (argb >> 24) & 0xff;
                    if (alpha < 16) transparent++;
                    sampled++;
                }
            }
            double ratio = sampled == 0 ? 0.0 : (double) transparent / sampled;
            if (ratio < MIN_TRANSPARENT_PIXEL_RATIO) {
                throw new FileValidationException(
                        "Image does not appear to have a removed background. Please upload a PNG with a transparent background.");
            }
        } catch (IOException e) {
            throw new FileValidationException("Failed to verify image transparency");
        }
    }

    private static void scanForMaliciousContent(MultipartFile file) {
        try (InputStream is = file.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            
            String line;
            int linesToRead = 200;
            int count = 0;
            while ((line = reader.readLine()) != null && count < linesToRead) {
                for (String signature : MALICIOUS_SIGNATURES) {
                    if (line.contains(signature)) {
                        throw new FileValidationException("File contains potentially malicious content: " + signature);
                    }
                }
                count++;
            }
        } catch (IOException e) {
        }
    }

    private static void verifyImageIntegrity(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            BufferedImage image = ImageIO.read(is);
            if (image == null) {
                throw new FileValidationException("Invalid image file or corrupted content");
            }
        } catch (IOException e) {
            throw new FileValidationException("Failed to verify image integrity");
        }
    }
}
