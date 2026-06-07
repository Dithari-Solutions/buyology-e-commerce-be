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

    // Documents (e.g. supplier trade licenses): PDFs or images.
    private static final Set<String> ALLOWED_DOCUMENT_EXTENSIONS = Set.of("pdf", "jpg", "jpeg", "png", "webp");
    private static final Set<String> ALLOWED_DOCUMENT_MIME_TYPES = Set.of(
            "application/pdf", "image/jpeg", "image/png", "image/webp");
    private static final long DOCUMENT_MAX_BYTES = 10L * 1024 * 1024; // 10 MB

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

    /** True if the given MIME type is an allowed image content type. */
    public static boolean isAllowedImageContentType(String contentType) {
        return contentType != null && ALLOWED_IMAGE_MIME_TYPES.contains(contentType);
    }

    /** True if the given MIME type is an allowed image OR video content type. */
    public static boolean isAllowedMediaContentType(String contentType) {
        return contentType != null
                && (ALLOWED_IMAGE_MIME_TYPES.contains(contentType) || ALLOWED_VIDEO_MIME_TYPES.contains(contentType));
    }

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

        // Magic-byte verification — do NOT trust the client-declared extension/MIME.
        // Covers webp and all video formats that the ImageIO check below cannot.
        verifyMediaMagic(file, allowVideo);

        // Extra integrity decode for raster images (skips webp — stock ImageIO can't read it).
        if (isImage && !extension.equals("webp")) {
            verifyImageIntegrity(file);
        }
    }

    /**
     * Verifies the file's leading bytes match an allowed image (or, when permitted,
     * video) container signature. Defeats content-type spoofing — e.g. a script or
     * executable renamed to .png with a forged image/png MIME.
     */
    private static void verifyMediaMagic(MultipartFile file, boolean allowVideo) {
        byte[] h;
        try (InputStream is = file.getInputStream()) {
            h = is.readNBytes(16);
        } catch (IOException e) {
            throw new FileValidationException("Failed to read file for validation");
        }
        boolean image = isPng(h) || isJpeg(h) || isGif(h) || isWebp(h);
        boolean video = allowVideo && (isMp4(h) || isAvi(h) || isMatroska(h));
        if (!image && !video) {
            throw new FileValidationException(
                    "File content does not match an allowed " + (allowVideo ? "image or video" : "image") + " format.");
        }
    }

    private static boolean startsWith(byte[] h, int off, int... sig) {
        if (h.length < off + sig.length) return false;
        for (int i = 0; i < sig.length; i++) {
            if ((h[off + i] & 0xFF) != (sig[i] & 0xFF)) return false;
        }
        return true;
    }

    private static boolean isPng(byte[] h)  { return startsWith(h, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A); }
    private static boolean isJpeg(byte[] h) { return startsWith(h, 0, 0xFF, 0xD8, 0xFF); }
    private static boolean isGif(byte[] h)  { return startsWith(h, 0, 0x47, 0x49, 0x46, 0x38); } // "GIF8"
    private static boolean isMatroska(byte[] h) { return startsWith(h, 0, 0x1A, 0x45, 0xDF, 0xA3); } // mkv/webm
    // RIFF container: "RIFF" then 4 size bytes then the form type at offset 8.
    private static boolean isWebp(byte[] h) { return startsWith(h, 0, 0x52, 0x49, 0x46, 0x46) && startsWith(h, 8, 0x57, 0x45, 0x42, 0x50); } // WEBP
    private static boolean isAvi(byte[] h)  { return startsWith(h, 0, 0x52, 0x49, 0x46, 0x46) && startsWith(h, 8, 0x41, 0x56, 0x49, 0x20); } // "AVI "
    // ISO-BMFF (mp4/mov/m4v): "ftyp" box type at offset 4.
    private static boolean isMp4(byte[] h)  { return startsWith(h, 4, 0x66, 0x74, 0x79, 0x70); }

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
        verifyMediaMagic(file, false); // reject content that isn't really an image

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

    /**
     * Validates an uploaded document (PDF or image): extension + declared MIME on an
     * allowlist, size cap, malicious-content scan, and magic-byte verification (so a
     * renamed executable / script cannot pass as a PDF or image).
     */
    public static void validateDocument(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return;
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new FileValidationException("File must have an extension");
        }
        String extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        String contentType = file.getContentType();

        if (!ALLOWED_DOCUMENT_EXTENSIONS.contains(extension)
                || contentType == null
                || !ALLOWED_DOCUMENT_MIME_TYPES.contains(contentType)) {
            throw new FileValidationException("Invalid document type. Only PDF or image files are allowed.");
        }
        if (file.getSize() > DOCUMENT_MAX_BYTES) {
            throw new FileValidationException(
                    "Document exceeds 10 MB limit (" + (file.getSize() / 1024 / 1024) + " MB).");
        }

        scanForMaliciousContent(file);

        // Magic-byte check — do not trust the client-declared extension/MIME alone.
        if ("pdf".equals(extension)) {
            verifyPdfMagic(file);
        } else if (!"webp".equals(extension)) {
            verifyImageIntegrity(file);
        }
    }

    private static void verifyPdfMagic(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            byte[] header = is.readNBytes(5);
            // "%PDF-"
            if (header.length < 5 || header[0] != '%' || header[1] != 'P'
                    || header[2] != 'D' || header[3] != 'F' || header[4] != '-') {
                throw new FileValidationException("File is not a valid PDF document");
            }
        } catch (IOException e) {
            throw new FileValidationException("Failed to verify document");
        }
    }

    /**
     * Strips a filename to a safe set of characters and removes any path components,
     * preventing path traversal / object-key injection when the name is used to build
     * a storage key. Returns "file" when nothing safe remains.
     */
    public static String sanitizeFilename(String name) {
        if (name == null) return "file";
        // Drop any directory parts a client may have included.
        String base = name.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) base = base.substring(slash + 1);
        base = base.replaceAll("[^A-Za-z0-9._-]", "_");
        // Collapse leading dots so the result can't become ".." or be hidden.
        base = base.replaceAll("^\\.+", "");
        return base.isBlank() ? "file" : base;
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
