package com.buyology.ecommerce.sell.service;

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
import com.buyology.ecommerce.sell.domain.SellRequest;
import com.buyology.ecommerce.sell.event.SellRequestSubmittedEvent;
import com.buyology.ecommerce.sell.repository.SellRequestRepository;
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
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Produces a <strong>preliminary, non-binding</strong> buy-back valuation with Claude, from the
 * customer's device photos, written description and declared condition, priced for the
 * <strong>UAE second-hand market in AED</strong>.
 *
 * <p>The mirror of {@link com.buyology.ecommerce.repair.service.RepairAiEstimateService}, with one
 * important difference: a repair is priced from a literal company price list, whereas a trade-in
 * has no fixed sheet — what a used device is worth depends on the model and the live market. So
 * {@code sell-pricing.json} carries the company's buy-back <em>policy</em> (margin, condition and
 * age multipliers, per-category floors, flat defect deductions, hard rules) and the model applies
 * it on top of the market value it estimates for that specific model.
 *
 * <p>Advisory only. The valuation never becomes the customer's offer: it is shown as "subject to
 * inspection" and offered to procurement as a starting point, but the binding offer is still sent
 * by a human via {@code SellService.setOffer}. Because of that, every failure here is swallowed — a
 * sell request must never fail, or change status, because the model was slow, rate-limited, or
 * unavailable.
 *
 * <p>The feature is inert unless {@code ANTHROPIC_API_KEY} is set (and
 * {@code sell.ai-estimate.enabled} is true): with a blank key no client is constructed, so the app
 * boots and the sell flow behaves exactly as it would without it.
 */
@Service
public class SellAiEstimateService {

    private static final Logger log = LoggerFactory.getLogger(SellAiEstimateService.class);

    /** The market the valuation is priced for. Conversion for display happens on read. */
    public static final String ESTIMATE_CURRENCY = "AED";

    // ── Token budget ────────────────────────────────────────────────────────
    // Same economics as the repair estimate: images dominate the cost, so we send few and small —
    // at most two photos, each re-encoded to JPEG at <=1024px on the long edge. That's plenty to
    // read a cracked screen or a scuffed chassis, and a fraction of the tokens a raw phone photo
    // would cost.
    private static final int MAX_IMAGES = 2;
    private static final int MAX_IMAGE_EDGE_PX = 1024;
    /** Skip anything larger — the API rejects oversized images and one photo shouldn't sink the call. */
    private static final long MAX_IMAGE_BYTES = 4_500_000L;
    private static final long MAX_TOKENS = 4_000L;

    /**
     * Base instructions. The company's buy-back policy is appended at construction to form the full
     * system prompt, so valuations follow OUR margins and deductions rather than the model's own
     * idea of a fair trade-in.
     */
    private static final String SYSTEM_PROMPT_BASE = """
            You are a trade-in buyer for a consumer electronics retailer in the UAE. You value
            second-hand devices the company is being offered, and you quote what the company will
            PAY the customer — never a retail price.

            How to value:
            - First estimate what this exact model, in good used condition, currently resells for
              in the UAE second-hand market (AED). Say so in your reasoning, not to the customer.
            - Then apply the company buy-back policy below, in this order: take the resale
              estimate, subtract the target margin, apply the condition multiplier for the
              customer's declared condition, apply the age multiplier from the purchase date, then
              subtract any flat deductions for defects you can see in the photos or that the
              customer describes.
            - Respect every hard rule. Never quote above max_offer_share_of_resale. If the result
              falls below the category floor, set purchasable false and explain plainly that the
              device is below what we can buy.
            - Give a min/max range, not a point price — the width should reflect how uncertain you
              are (see range_width limits in the policy), and min must never exceed max.
            - Judge the condition yourself from the photos and report it in observedCondition using
              exactly one of LIKE_NEW, GOOD, FAIR, POOR. If it disagrees with what the customer
              declared, value the device at YOUR reading and say so in the assessment.
            - Confidence: HIGH = the model is unambiguous and the photos show it clearly;
              MEDIUM = the model is clear but condition or storage/spec is uncertain; LOW = you are
              largely guessing (unknown model, unusable photos, no purchase date).

            The assessment is shown to the customer: one or two plain sentences, no markdown, no
            price (the price is carried separately), and never promise a final offer — our team
            inspects the device before making one. Be brief; do not over-deliberate.
            """;

    private final SellRequestRepository sellRepo;
    private final ContaboObjectService contaboObjectService;
    private final String model;
    /** Base instructions + the company buy-back policy — built once, reused for every request. */
    private final String systemPrompt;
    /** Null when the feature is disabled or no API key is configured — every call then no-ops. */
    private final AnthropicClient client;

    public SellAiEstimateService(SellRequestRepository sellRepo,
                                 ContaboObjectService contaboObjectService,
                                 @Value("${anthropic.api-key:}") String apiKey,
                                 @Value("${anthropic.model:claude-opus-4-8}") String model,
                                 @Value("${sell.ai-estimate.enabled:true}") boolean enabled,
                                 @Value("${sell.pricing.path:}") String pricingPath) {
        this.sellRepo = sellRepo;
        this.contaboObjectService = contaboObjectService;
        this.model = (model == null || model.isBlank()) ? "claude-opus-4-8" : model.trim();
        this.systemPrompt = buildSystemPrompt(pricingPath);

        AnthropicClient built = null;
        if (enabled && apiKey != null && !apiKey.isBlank()) {
            try {
                built = AnthropicOkHttpClient.builder()
                        .apiKey(apiKey.trim())
                        .timeout(Duration.ofMinutes(3))
                        .build();
                log.info("[SELL-AI] Buy-back valuation enabled (model={}).", this.model);
            } catch (Exception e) {
                // Never block startup on a bad key — the feature simply stays off.
                log.error("[SELL-AI] Failed to build the Anthropic client; valuations disabled.", e);
            }
        } else {
            log.info("[SELL-AI] Buy-back valuation disabled (no ANTHROPIC_API_KEY or feature flag off).");
        }
        this.client = built;
    }

    /**
     * Builds the system prompt: base instructions + the company buy-back policy. The policy loads
     * from {@code sell.pricing.path} (an external file, so margins can be tuned on the server
     * without a rebuild) when set and readable, otherwise from the bundled {@code sell-pricing.json}
     * classpath resource. If neither can be read the model still runs on the base instructions
     * alone — it just falls back to a generic UAE-market trade-in estimate rather than our policy.
     */
    private static String buildSystemPrompt(String pricingPath) {
        String pricing = loadPricing(pricingPath);
        if (pricing == null || pricing.isBlank()) {
            log.warn("[SELL-AI] No buy-back policy loaded — valuations will use generic UAE pricing.");
            return SYSTEM_PROMPT_BASE;
        }
        log.info("[SELL-AI] Loaded buy-back policy ({} chars).", pricing.length());
        return SYSTEM_PROMPT_BASE + "\n\nCOMPANY BUY-BACK POLICY (authoritative, AED):\n" + pricing.trim();
    }

    private static String loadPricing(String pricingPath) {
        // External override first — lets margins change without redeploying the jar.
        if (pricingPath != null && !pricingPath.isBlank()) {
            try {
                return Files.readString(Path.of(pricingPath.trim()));
            } catch (Exception e) {
                log.error("[SELL-AI] Could not read sell.pricing.path={}; falling back to the "
                        + "bundled policy.", pricingPath, e);
            }
        }
        try (InputStream in = SellAiEstimateService.class.getResourceAsStream("/sell-pricing.json")) {
            if (in == null) return null;
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("[SELL-AI] Could not read the bundled sell-pricing.json.", e);
            return null;
        }
    }

    /** True when a valuation can actually be produced. */
    public boolean isEnabled() {
        return client != null;
    }

    /**
     * Values a freshly-submitted sell request off the request thread. Runs AFTER_COMMIT so the row
     * is guaranteed visible to this thread, and {@code @Async} so the customer's submit response is
     * never held up by a multi-second vision call.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSellRequestSubmitted(SellRequestSubmittedEvent event) {
        estimate(event.sellRequestId());
    }

    /**
     * Produces and persists the valuation for one sell request. Safe to call repeatedly (it simply
     * overwrites); returns quietly when the feature is off or the request no longer exists.
     */
    public void estimate(UUID sellRequestId) {
        if (!isEnabled() || sellRequestId == null) {
            return;
        }
        SellRequest request = sellRepo.findById(sellRequestId).orElse(null);
        if (request == null) {
            return;
        }
        try {
            SellValuation valuation = callClaude(request);
            if (valuation == null) {
                log.warn("[SELL-AI] No valuation returned for sell request {}.", sellRequestId);
                return;
            }
            apply(request, valuation);
            sellRepo.save(request);
            log.info("[SELL-AI] Valued sell request {} at {} {}-{} (confidence={}, observed={}).",
                    request.getReference(), ESTIMATE_CURRENCY,
                    request.getAiEstimateMinPrice(), request.getAiEstimateMaxPrice(),
                    request.getAiEstimateConfidence(), request.getAiEstimateCondition());
        } catch (Exception e) {
            // Advisory feature — a failure must never surface to the customer or change the request.
            log.error("[SELL-AI] Valuation failed for sell request {}.", sellRequestId, e);
        }
    }

    // =========================================================================
    // Claude call
    // =========================================================================

    private SellValuation callClaude(SellRequest request) {
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

        StructuredMessageCreateParams<SellValuation> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(MAX_TOKENS)
                .thinking(ThinkingConfigAdaptive.builder().build())
                .system(systemPrompt)
                .addUserMessageOfBlockParams(blocks)
                .outputConfig(SellValuation.class)
                .build();

        Optional<SellValuation> parsed = client.messages().create(params).content().stream()
                .flatMap(block -> block.text().stream())
                .map(text -> text.text())
                .findFirst();
        return parsed.orElse(null);
    }

    private static String buildPrompt(SellRequest request, int imageCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("Value this device for trade-in — what will we pay the customer? UAE market.\n\n");
        sb.append("Product: ").append(nullSafe(request.getProductName())).append('\n');
        sb.append("Brand: ").append(nullSafe(request.getBrand())).append('\n');
        sb.append("Model: ").append(nullSafe(request.getModel())).append('\n');
        sb.append("Condition declared by the customer: ")
          .append(request.getDeviceCondition() == null ? "—" : request.getDeviceCondition().name()).append('\n');
        if (request.getPurchaseDate() != null) {
            sb.append("Purchased: ").append(request.getPurchaseDate()).append('\n');
        } else {
            sb.append("Purchased: not stated — assume 2-3 years old and lower your confidence.\n");
        }
        sb.append("\nCustomer's description of the device:\n")
          .append(nullSafe(request.getDescription())).append('\n');
        sb.append("\n").append(imageCount > 0
                ? imageCount + " photo(s) of the device are attached above."
                : "No usable photos were provided — value from the description alone and lower your confidence.");
        return sb.toString();
    }

    /** An image ready to send: bytes plus the media type that actually describes them. */
    private record Photo(byte[] data, Base64ImageSource.MediaType mediaType) {
    }

    /**
     * Shrinks a customer photo before it is billed as input tokens: re-encodes to JPEG at no more
     * than {@link #MAX_IMAGE_EDGE_PX} on the long edge. Falls back to the original bytes if the
     * image can't be decoded (an unreadable photo is still worth sending as-is).
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

    private static void apply(SellRequest request, SellValuation valuation) {
        BigDecimal min = money(valuation.minOfferAed());
        BigDecimal max = money(valuation.maxOfferAed());
        // Tolerate a model that swaps the bounds rather than showing an inverted range.
        if (min != null && max != null && min.compareTo(max) > 0) {
            BigDecimal swap = min;
            min = max;
            max = swap;
        }
        request.setAiEstimateMinPrice(min);
        request.setAiEstimateMaxPrice(max);
        request.setAiEstimateCurrency(ESTIMATE_CURRENCY);
        request.setAiEstimateConfidence(confidence(valuation.confidence()));
        request.setAiEstimateSummary(trim(valuation.assessment(), 2000));
        request.setAiEstimateCondition(observedCondition(valuation.observedCondition()));
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

    /** Normalise to a {@link com.buyology.ecommerce.sell.domain.DeviceCondition} name, or null. */
    private static String observedCondition(String raw) {
        if (raw == null) return null;
        String upper = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        return switch (upper) {
            case "LIKE_NEW", "GOOD", "FAIR", "POOR" -> upper;
            default -> null;
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

    /** The shape Claude is constrained to return. Prices are what WE pay, in AED. */
    public record SellValuation(
            @JsonPropertyDescription("One or two plain sentences on the device's condition and what "
                    + "drives its value. Shown to the customer. No markdown, no price.")
            String assessment,

            @JsonPropertyDescription("Low end of what the company should pay the customer, in AED.")
            Double minOfferAed,

            @JsonPropertyDescription("High end of what the company should pay the customer, in AED.")
            Double maxOfferAed,

            @JsonPropertyDescription("Condition you read off the photos. Exactly one of: "
                    + "LIKE_NEW, GOOD, FAIR, POOR.")
            String observedCondition,

            @JsonPropertyDescription("Exactly one of: HIGH, MEDIUM, LOW.")
            String confidence,

            @JsonPropertyDescription("False when the device is below the category floor or hits a "
                    + "never-buy rule, i.e. we should not buy it at all.")
            Boolean purchasable
    ) {
    }
}
