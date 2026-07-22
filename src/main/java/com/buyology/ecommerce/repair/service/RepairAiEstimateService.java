package com.buyology.ecommerce.repair.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import com.buyology.ecommerce.infrastructure.external.ContaboObjectService;
import com.buyology.ecommerce.repair.domain.RepairRequest;
import com.buyology.ecommerce.repair.event.RepairSubmittedEvent;
import com.buyology.ecommerce.repair.repository.RepairRequestRepository;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Produces a <strong>preliminary, non-binding</strong> repair price estimate with Claude, from the
 * customer's problem photos plus their written description, priced for the <strong>UAE market in
 * AED</strong>.
 *
 * <p>Advisory only. The estimate never becomes the customer's quote: it is shown to the customer as
 * "subject to inspection" and offered to the repair team as a starting point, but the binding price
 * is still sent by a human via {@code RepairService.setPrice}. Because of that, every failure here
 * is swallowed — a repair request must never fail, or change status, because the model was slow,
 * rate-limited, or unavailable.
 *
 * <p>The feature is inert unless {@code ANTHROPIC_API_KEY} is set (and
 * {@code repair.ai-estimate.enabled} is true): with a blank key no client is constructed, so the
 * app boots and the repair flow behaves exactly as it did before.
 */
@Service
public class RepairAiEstimateService {

    private static final Logger log = LoggerFactory.getLogger(RepairAiEstimateService.class);

    /** The market the estimate is priced for. Conversion for display happens on read. */
    public static final String ESTIMATE_CURRENCY = "AED";

    // ── Token budget ────────────────────────────────────────────────────────
    // Images dominate the cost of this call, so we send few and small: at most two photos,
    // each re-encoded to JPEG at <=1024px on the long edge (a full-resolution phone photo
    // costs several times more input tokens than it's worth for a rough estimate). The
    // output ceiling then hard-bounds thinking + JSON for the rest.
    private static final int MAX_IMAGES = 2;
    private static final int MAX_IMAGE_EDGE_PX = 1024;
    /** Skip anything larger — the API rejects oversized images and one photo shouldn't sink the call. */
    private static final long MAX_IMAGE_BYTES = 4_500_000L;
    private static final long MAX_TOKENS = 4_000L;

    private static final String SYSTEM_PROMPT = """
            You are a device-repair estimator for a consumer electronics retailer in the UAE.

            - Price the repair for the UAE market in AED, parts and labour included.
            - Give a realistic min/max range, never a single number. Widen the range when the \
              photos and description leave the fault uncertain.
            - Estimate only from what is visible or stated. Do not invent damage or assume the \
              best case. If information is too thin to price well, still give a range and set \
              confidence to LOW, noting what is missing.
            - Set repairable false if the device is likely beyond economical repair.
            - Confidence: HIGH = fault clear and part cost known; MEDIUM = likely fault known but \
              extent unclear; LOW = largely guessing.

            The diagnosis is shown to the customer: one or two plain sentences, no markdown, no \
            price, and never promise a final price — a technician inspects the device first.
            Be brief; do not over-deliberate.
            """;

    private final RepairRequestRepository repairRepo;
    private final ContaboObjectService contaboObjectService;
    private final String model;
    /** Null when the feature is disabled or no API key is configured — every call then no-ops. */
    private final AnthropicClient client;

    public RepairAiEstimateService(RepairRequestRepository repairRepo,
                                   ContaboObjectService contaboObjectService,
                                   @Value("${anthropic.api-key:}") String apiKey,
                                   @Value("${anthropic.model:claude-opus-4-8}") String model,
                                   @Value("${repair.ai-estimate.enabled:true}") boolean enabled) {
        this.repairRepo = repairRepo;
        this.contaboObjectService = contaboObjectService;
        this.model = (model == null || model.isBlank()) ? "claude-opus-4-8" : model.trim();

        AnthropicClient built = null;
        if (enabled && apiKey != null && !apiKey.isBlank()) {
            try {
                built = AnthropicOkHttpClient.builder()
                        .apiKey(apiKey.trim())
                        .timeout(Duration.ofMinutes(3))
                        .build();
                log.info("[REPAIR-AI] Price estimation enabled (model={}).", this.model);
            } catch (Exception e) {
                // Never block startup on a bad key — the feature simply stays off.
                log.error("[REPAIR-AI] Failed to build the Anthropic client; estimates disabled.", e);
            }
        } else {
            log.info("[REPAIR-AI] Price estimation disabled (no ANTHROPIC_API_KEY or feature flag off).");
        }
        this.client = built;
    }

    /** True when an estimate can actually be produced. */
    public boolean isEnabled() {
        return client != null;
    }

    /**
     * Estimates a freshly-submitted repair off the request thread. Runs AFTER_COMMIT so the row is
     * guaranteed visible to this thread, and {@code @Async} so the customer's submit response is
     * never held up by a multi-second vision call.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRepairSubmitted(RepairSubmittedEvent event) {
        estimate(event.repairId());
    }

    /**
     * Produces and persists the estimate for one repair. Safe to call repeatedly (it simply
     * overwrites); returns quietly when the feature is off or the repair no longer exists.
     */
    public void estimate(UUID repairId) {
        if (!isEnabled() || repairId == null) {
            return;
        }
        RepairRequest request = repairRepo.findById(repairId).orElse(null);
        if (request == null) {
            return;
        }
        try {
            RepairEstimate estimate = callClaude(request);
            if (estimate == null) {
                log.warn("[REPAIR-AI] No estimate returned for repair {}.", repairId);
                return;
            }
            apply(request, estimate);
            repairRepo.save(request);
            log.info("[REPAIR-AI] Estimated repair {} at {} {}-{} (confidence={}).",
                    request.getReference(), ESTIMATE_CURRENCY,
                    request.getAiEstimateMinPrice(), request.getAiEstimateMaxPrice(),
                    request.getAiEstimateConfidence());
        } catch (Exception e) {
            // Advisory feature — a failure must never surface to the customer or change the request.
            log.error("[REPAIR-AI] Estimate failed for repair {}.", repairId, e);
        }
    }

    // =========================================================================
    // Claude call
    // =========================================================================

    private RepairEstimate callClaude(RepairRequest request) {
        List<ContentBlockParam> blocks = new ArrayList<>();

        // Photos first, then the text that refers to them.
        int attached = 0;
        if (request.getImageKeys() != null && !request.getImageKeys().isBlank()) {
            for (String key : request.getImageKeys().split("\n")) {
                if (attached >= MAX_IMAGES) break;
                if (key == null || key.isBlank()) continue;
                byte[] raw = contaboObjectService.downloadBytes(key.trim());
                if (raw == null || raw.length == 0 || raw.length > MAX_IMAGE_BYTES) {
                    continue;
                }
                Photo photo = shrink(raw, key);
                blocks.add(ContentBlockParam.ofImage(ImageBlockParam.builder()
                        .source(Base64ImageSource.builder()
                                .data(Base64.getEncoder().encodeToString(photo.data()))
                                .mediaType(photo.mediaType())
                                .build())
                        .build()));
                attached++;
            }
        }

        blocks.add(ContentBlockParam.ofText(TextBlockParam.builder()
                .text(buildPrompt(request, attached))
                .build()));

        StructuredMessageCreateParams<RepairEstimate> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(MAX_TOKENS)
                .thinking(ThinkingConfigAdaptive.builder().build())
                .system(SYSTEM_PROMPT)
                .addUserMessageOfBlockParams(blocks)
                .outputConfig(RepairEstimate.class)
                .build();

        Optional<RepairEstimate> parsed = client.messages().create(params).content().stream()
                .flatMap(block -> block.text().stream())
                .map(text -> text.text())
                .findFirst();
        return parsed.orElse(null);
    }

    private static String buildPrompt(RepairRequest request, int imageCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("Estimate the cost of repairing this device for the UAE market.\n\n");
        sb.append("Product: ").append(nullSafe(request.getProductName())).append('\n');
        sb.append("Brand: ").append(nullSafe(request.getBrand())).append('\n');
        sb.append("Model: ").append(nullSafe(request.getModel())).append('\n');
        if (request.getPurchaseDate() != null) {
            sb.append("Purchased: ").append(request.getPurchaseDate()).append('\n');
        }
        sb.append("\nCustomer's description of the problem:\n")
          .append(nullSafe(request.getDescription())).append('\n');
        sb.append("\n").append(imageCount > 0
                ? imageCount + " photo(s) of the fault are attached above."
                : "No usable photos were provided — estimate from the description alone.");
        return sb.toString();
    }

    /** An image ready to send: bytes plus the media type that actually describes them. */
    private record Photo(byte[] data, Base64ImageSource.MediaType mediaType) {
    }

    /**
     * Shrinks a customer photo before it is billed as input tokens: re-encodes to JPEG at no more
     * than {@link #MAX_IMAGE_EDGE_PX} on the long edge. A modern phone photo is several megapixels,
     * which costs several times more input tokens than a rough estimate justifies — 1024px is
     * plenty to see a cracked screen or a bent port. Falls back to the original bytes if the image
     * can't be decoded (an unreadable photo is still worth sending as-is).
     */
    private static Photo shrink(byte[] original, String key) {
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(original));
            if (src == null) {
                return new Photo(original, mediaType(key));
            }
            int width = src.getWidth();
            int height = src.getHeight();
            int longEdge = Math.max(width, height);
            double scale = longEdge > MAX_IMAGE_EDGE_PX ? (double) MAX_IMAGE_EDGE_PX / longEdge : 1.0;
            int targetW = Math.max(1, (int) Math.round(width * scale));
            int targetH = Math.max(1, (int) Math.round(height * scale));

            // TYPE_INT_RGB drops any alpha channel, which JPEG can't carry anyway.
            BufferedImage dst = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = dst.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, targetW, targetH, null);
            g.dispose();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(dst, "jpeg", out) || out.size() == 0) {
                return new Photo(original, mediaType(key));
            }
            return new Photo(out.toByteArray(), Base64ImageSource.MediaType.IMAGE_JPEG);
        } catch (Exception e) {
            return new Photo(original, mediaType(key));
        }
    }

    private static Base64ImageSource.MediaType mediaType(String key) {
        return switch (ContaboObjectService.contentTypeForKey(key)) {
            case "image/png" -> Base64ImageSource.MediaType.IMAGE_PNG;
            case "image/webp" -> Base64ImageSource.MediaType.IMAGE_WEBP;
            case "image/gif" -> Base64ImageSource.MediaType.IMAGE_GIF;
            default -> Base64ImageSource.MediaType.IMAGE_JPEG;
        };
    }

    // =========================================================================
    // Persistence
    // =========================================================================

    private static void apply(RepairRequest request, RepairEstimate estimate) {
        BigDecimal min = money(estimate.minPriceAed());
        BigDecimal max = money(estimate.maxPriceAed());
        // Tolerate a model that swaps the bounds rather than showing an inverted range.
        if (min != null && max != null && min.compareTo(max) > 0) {
            BigDecimal swap = min;
            min = max;
            max = swap;
        }
        request.setAiEstimateMinPrice(min);
        request.setAiEstimateMaxPrice(max);
        request.setAiEstimateCurrency(ESTIMATE_CURRENCY);
        request.setAiEstimateConfidence(confidence(estimate.confidence()));
        request.setAiEstimateSummary(trim(estimate.diagnosis(), 2000));
        request.setAiEstimateTime(trim(estimate.estimatedTime(), 120));
        request.setAiEstimatedAt(Instant.now());
    }

    private static BigDecimal money(Double value) {
        if (value == null || value.isNaN() || value.isInfinite() || value <= 0) {
            return null;
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    /** Normalise to LOW/MEDIUM/HIGH; anything unexpected is treated as LOW. */
    private static String confidence(String raw) {
        if (raw == null) return "LOW";
        String upper = raw.trim().toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "HIGH", "MEDIUM", "LOW" -> upper;
            default -> "LOW";
        };
    }

    private static String trim(String value, int max) {
        if (value == null) return null;
        String cleaned = value.trim();
        if (cleaned.isEmpty()) return null;
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max);
    }

    private static String nullSafe(String value) {
        return value == null ? "—" : value;
    }

    // =========================================================================
    // Structured output schema
    // =========================================================================

    /** The shape Claude is constrained to return. Prices are AED, inclusive of parts and labour. */
    public record RepairEstimate(
            @JsonPropertyDescription("One or two plain sentences describing the likely fault and "
                    + "what the repair involves. Shown to the customer. No markdown, no price.")
            String diagnosis,

            @JsonPropertyDescription("Low end of the estimated total repair cost in AED, parts and labour included.")
            Double minPriceAed,

            @JsonPropertyDescription("High end of the estimated total repair cost in AED, parts and labour included.")
            Double maxPriceAed,

            @JsonPropertyDescription("Expected turnaround as free text, e.g. '3-5 business days'.")
            String estimatedTime,

            @JsonPropertyDescription("Exactly one of: HIGH, MEDIUM, LOW.")
            String confidence,

            @JsonPropertyDescription("False when the device is likely beyond economical repair.")
            Boolean repairable
    ) {
    }
}
