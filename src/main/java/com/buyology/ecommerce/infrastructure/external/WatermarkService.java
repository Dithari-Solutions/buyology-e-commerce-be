package com.buyology.ecommerce.infrastructure.external;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Optional;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * Stamps the Buyology logo onto uploaded catalog/marketing images so the assets
 * are visibly ours.
 *
 * The logo is drawn semi-transparently in the center, scaled
 * relative to the image width. Transparency is always preserved: any source with an
 * alpha channel is emitted as PNG, never JPEG (JPEG cannot store alpha — transparent
 * pixels would flatten to black). Opaque JPEGs stay JPEG; WebP
 * (which ImageIO cannot write) is re-encoded to PNG. Anything we can't decode
 * (animated GIF, unknown formats, or a WebP plugin that failed to load) is left
 * untouched — {@link #apply} returns empty and the caller uploads the original.
 * It never throws: watermarking must never block an upload.
 */
@Service
public class WatermarkService {

    private static final Logger log = LoggerFactory.getLogger(WatermarkService.class);
    private static final String LOGO_RESOURCE = "report/watermark-logo.png";

    private final boolean enabled;
    private final float opacity;   // 0..1
    private final double scale;    // logo width as a fraction of the image width
    private final double margin;   // margin from the edges as a fraction of image width

    private volatile BufferedImage logo;
    private volatile boolean logoLoadFailed;

    public WatermarkService(
            @Value("${watermark.enabled:true}") boolean enabled,
            @Value("${watermark.opacity:0.55}") float opacity,
            @Value("${watermark.scale:0.20}") double scale,
            @Value("${watermark.margin:0.03}") double margin) {
        this.enabled = enabled;
        this.opacity = clamp(opacity, 0.05f, 1f);
        this.scale = Math.max(0.02, Math.min(scale, 1.0));
        this.margin = Math.max(0.0, Math.min(margin, 0.25));
        // Register classpath ImageIO plugins (e.g. TwelveMonkeys WebP) under the
        // application classloader — required inside a Spring Boot fat jar.
        try {
            ImageIO.scanForPlugins();
        } catch (Throwable t) {
            log.warn("ImageIO.scanForPlugins() failed: {}", t.toString());
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** The watermarked result: bytes plus the (possibly changed) content type and extension. */
    public record Result(byte[] bytes, String contentType, String extension) {}

    /**
     * Apply the watermark to an image. Returns empty when watermarking is disabled,
     * the format is unsupported, or anything goes wrong — in which case the caller
     * should upload the original bytes unchanged.
     */
    public Optional<Result> apply(byte[] input, String contentType) {
        if (!enabled || input == null || input.length == 0) {
            return Optional.empty();
        }
        String fmt = formatOf(contentType);
        if (fmt == null) {
            return Optional.empty(); // not a format we watermark (gif/video/unknown)
        }
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(input));
            if (src == null) {
                // e.g. WebP without a registered reader — leave the upload untouched.
                log.debug("Watermark skipped: could not decode {} image", contentType);
                return Optional.empty();
            }

            // Keep transparency: a source with an alpha channel is rendered onto a
            // transparent ARGB canvas and emitted as PNG. JPEG cannot store alpha, so
            // forcing a transparent image through the JPEG path would flatten its
            // transparent pixels to black — never do that. Only opaque sources stay JPEG.
            boolean hasAlpha = src.getColorModel().hasAlpha();
            boolean asJpeg = "jpeg".equals(fmt) && !hasAlpha;
            int type = asJpeg ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB;
            BufferedImage canvas = new BufferedImage(src.getWidth(), src.getHeight(), type);

            Graphics2D g = canvas.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(src, 0, 0, null);
            drawLogo(g, canvas.getWidth(), canvas.getHeight());
            g.dispose();

            if (asJpeg) {
                return Optional.of(new Result(encodeJpeg(canvas), "image/jpeg", "jpg"));
            }
            return Optional.of(new Result(encodePng(canvas), "image/png", "png"));
        } catch (Throwable t) {
            log.warn("Watermarking failed for {} — uploading original. Cause: {}", contentType, t.toString());
            return Optional.empty();
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private void drawLogo(Graphics2D g, int imgW, int imgH) {
        BufferedImage l = logo();
        if (l == null) return;

        int targetW = (int) Math.round(imgW * scale);
        if (targetW < 8) return; // too small to bother
        int targetH = (int) Math.round((double) l.getHeight() / l.getWidth() * targetW);
        if (targetH < 1 || targetH > imgH) return;

        // Center the logo on the image.
        int x = (imgW - targetW) / 2;
        int y = (imgH - targetH) / 2;
        if (x < 0) x = 0;
        if (y < 0) y = 0;

        var previous = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
        g.drawImage(l, x, y, targetW, targetH, null);
        g.setComposite(previous);
    }

    /** Lazily load + cache the logo. Returns null if it can't be read (watermark then no-ops). */
    private BufferedImage logo() {
        if (logo == null && !logoLoadFailed) {
            synchronized (this) {
                if (logo == null && !logoLoadFailed) {
                    try (var in = new ClassPathResource(LOGO_RESOURCE).getInputStream()) {
                        BufferedImage loaded = ImageIO.read(in);
                        if (loaded == null) {
                            logoLoadFailed = true;
                            log.warn("Watermark logo {} could not be decoded", LOGO_RESOURCE);
                        } else {
                            logo = loaded;
                        }
                    } catch (Exception e) {
                        logoLoadFailed = true;
                        log.warn("Watermark logo {} not found: {}", LOGO_RESOURCE, e.getMessage());
                    }
                }
            }
        }
        return logo;
    }

    private static byte[] encodePng(BufferedImage image) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static byte[] encodeJpeg(BufferedImage image) throws Exception {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                writer.setOutput(ios);
                ImageWriteParam param = writer.getDefaultWriteParam();
                if (param.canWriteCompressed()) {
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(0.9f);
                }
                writer.write(null, new IIOImage(image, null, null), param);
            }
            return baos.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    /** Maps a content type to the watermarkable format key, or null if we don't watermark it. */
    private static String formatOf(String contentType) {
        if (contentType == null) return null;
        return switch (contentType.toLowerCase()) {
            case "image/jpeg", "image/jpg" -> "jpeg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> null; // gif, video/*, application/pdf, etc.
        };
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(v, hi));
    }
}
