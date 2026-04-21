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
