package com.buyology.ecommerce.common.utils;

import com.buyology.ecommerce.common.exception.FileValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileValidationUtilsTest {

    // RIFF....WEBP container header (enough for the magic-byte check).
    private static byte[] webpBytes() {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.writeBytes(new byte[]{'R', 'I', 'F', 'F'});
        b.writeBytes(new byte[]{0, 0, 0, 0});
        b.writeBytes(new byte[]{'W', 'E', 'B', 'P'});
        b.writeBytes(new byte[]{'V', 'P', '8', ' '}); // padding
        return b.toByteArray();
    }

    private static byte[] pdfBytes() {
        return "%PDF-1.4\n%âãÏÓ\n1 0 obj".getBytes();
    }

    // ── Image content-type spoofing ──────────────────────────────────────────

    @Test
    void rejectsScriptDisguisedAsPng() {
        // .png extension + forged image/png MIME but the body is a script.
        MockMultipartFile f = new MockMultipartFile(
                "file", "evil.png", "image/png", "<script>alert(1)</script>".getBytes());
        assertThrows(FileValidationException.class, () -> FileValidationUtils.validateImage(f));
    }

    @Test
    void rejectsBinaryWithWrongMagicAsPng() {
        // Forged MIME/extension, but bytes are neither image nor script — magic check fails.
        MockMultipartFile f = new MockMultipartFile(
                "file", "fake.png", "image/png", new byte[]{0x00, 0x01, 0x02, 0x03, 0x04, 0x05});
        assertThrows(FileValidationException.class, () -> FileValidationUtils.validateImage(f));
    }

    @Test
    void acceptsRealWebpImage() {
        MockMultipartFile f = new MockMultipartFile("file", "ok.webp", "image/webp", webpBytes());
        assertDoesNotThrow(() -> FileValidationUtils.validateImage(f));
    }

    // ── Document validation ──────────────────────────────────────────────────

    @Test
    void acceptsRealPdfDocument() {
        MockMultipartFile f = new MockMultipartFile("file", "license.pdf", "application/pdf", pdfBytes());
        assertDoesNotThrow(() -> FileValidationUtils.validateDocument(f));
    }

    @Test
    void rejectsNonPdfDisguisedAsPdf() {
        MockMultipartFile f = new MockMultipartFile(
                "file", "evil.pdf", "application/pdf", "GIF89a not really a pdf".getBytes());
        assertThrows(FileValidationException.class, () -> FileValidationUtils.validateDocument(f));
    }

    @Test
    void rejectsDisallowedDocumentExtension() {
        MockMultipartFile f = new MockMultipartFile(
                "file", "payload.exe", "application/octet-stream", pdfBytes());
        assertThrows(FileValidationException.class, () -> FileValidationUtils.validateDocument(f));
    }

    // ── Filename sanitization ────────────────────────────────────────────────

    @Test
    void sanitizeStripsPathTraversal() {
        // Directory components are dropped entirely — only a safe basename survives.
        assertEquals("passwd", FileValidationUtils.sanitizeFilename("../../etc/passwd"));
        assertEquals("file.png", FileValidationUtils.sanitizeFilename("/abs/path/file.png"));
    }

    @Test
    void sanitizeReplacesUnsafeCharsAndStripsLeadingDots() {
        assertEquals("my_file.png", FileValidationUtils.sanitizeFilename("my file.png"));
        assertEquals("file", FileValidationUtils.sanitizeFilename(".."));
        assertEquals("file", FileValidationUtils.sanitizeFilename(null));
    }
}
