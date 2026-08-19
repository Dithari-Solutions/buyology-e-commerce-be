package com.buyology.ecommerce.assistant.service;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.refund.dto.PublicRefundSettingResponse;
import com.buyology.ecommerce.refund.service.RefundSettingService;
import com.buyology.ecommerce.store.dto.OperatingHoursResponse;
import com.buyology.ecommerce.store.dto.StoreLocationResponse;
import com.buyology.ecommerce.store.dto.StoreResponse;
import com.buyology.ecommerce.store.service.StoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Everything the assistant knows about the company that is not a product.
 *
 * <p>Two sources, deliberately kept apart:
 *
 * <ul>
 *   <li><strong>Live</strong> — branches, opening hours, contact details and the return window are
 *       read from the same tables the storefront renders from. Anything the business can change in
 *       the admin dashboard must not also need editing in a text file, or the assistant will
 *       eventually quote last quarter's opening hours with total confidence.</li>
 *   <li><strong>Curated</strong> — the policies, positioning and stock answers that live nowhere in
 *       the schema, held in {@code assistant-knowledge.md}. It ships bundled and can be replaced at
 *       runtime via {@code assistant.knowledge.path}, mirroring how repair and sell pricing are
 *       overridden, so support can correct an answer without a rebuild.</li>
 * </ul>
 *
 * <p>The curated file is the assistant's only source of company policy. Whatever it does not cover,
 * the assistant is instructed to hand to a human rather than improvise — an invented return policy
 * is a promise the business has to keep.
 */
@Service
public class AssistantCompanyService {

    private static final Logger log = LoggerFactory.getLogger(AssistantCompanyService.class);

    /** Live company facts change rarely; a few minutes of staleness is invisible to a shopper. */
    private static final long LIVE_FACTS_TTL_MS = 300_000L;

    private final StoreService storeService;
    private final RefundSettingService refundSettingService;

    /** The curated knowledge base, read once at startup. */
    private final String knowledgeBase;

    private final AtomicReference<CachedFacts> liveFacts = new AtomicReference<>();

    public AssistantCompanyService(StoreService storeService,
                                   RefundSettingService refundSettingService,
                                   @Value("${assistant.knowledge.path:}") String knowledgePath) {
        this.storeService = storeService;
        this.refundSettingService = refundSettingService;
        this.knowledgeBase = loadKnowledge(knowledgePath);
    }

    private record CachedFacts(String text, long builtAtMs) {
    }

    /** The curated company knowledge base, or an empty string when none could be read. */
    public String knowledgeBase() {
        return knowledgeBase;
    }

    /**
     * Live company facts, rendered for the prompt: branches with addresses and opening hours,
     * contact details, and the current return window.
     */
    @Transactional(readOnly = true)
    public String liveFacts() {
        CachedFacts cached = liveFacts.get();
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.builtAtMs() < LIVE_FACTS_TTL_MS) {
            return cached.text();
        }
        String built = buildLiveFacts();
        liveFacts.set(new CachedFacts(built, now));
        return built;
    }

    private String buildLiveFacts() {
        StringBuilder sb = new StringBuilder();

        try {
            PublicRefundSettingResponse refund = refundSettingService.getPublicResponse();
            if (refund != null) {
                if (refund.enabled() && refund.returnWindowDays() != null) {
                    sb.append("Returns: customers may return an eligible order within ")
                      .append(refund.returnWindowDays()).append(" days of delivery.\n");
                } else if (!refund.enabled()) {
                    sb.append("Returns: online returns are currently not being accepted — "
                            + "direct the customer to support.\n");
                }
            }
        } catch (Exception e) {
            log.warn("[ASSISTANT] Could not read the public refund settings.", e);
        }

        try {
            List<StoreResponse> stores = unwrapList(storeService.getPublicStores());
            if (!stores.isEmpty()) {
                sb.append("\nStores and branches:\n");
                for (StoreResponse store : stores) {
                    sb.append("- ").append(nullSafe(store.getName()));
                    if (store.getCountryName() != null) {
                        sb.append(" (").append(store.getCountryName()).append(')');
                    }
                    sb.append('\n');
                    if (store.getContactPhone() != null && !store.getContactPhone().isBlank()) {
                        sb.append("  phone: ").append(store.getContactPhone()).append('\n');
                    }
                    if (store.getContactEmail() != null && !store.getContactEmail().isBlank()) {
                        sb.append("  email: ").append(store.getContactEmail()).append('\n');
                    }
                    appendLocations(sb, store.getLocations());
                }
            }
        } catch (Exception e) {
            log.warn("[ASSISTANT] Could not read the public store list.", e);
        }

        return sb.toString().trim();
    }

    private static void appendLocations(StringBuilder sb, List<StoreLocationResponse> locations) {
        if (locations == null || locations.isEmpty()) {
            return;
        }
        for (StoreLocationResponse location : locations) {
            sb.append("  branch: ").append(nullSafe(location.getBranchName()));
            List<String> address = new ArrayList<>();
            if (location.getAddress() != null && !location.getAddress().isBlank()) address.add(location.getAddress());
            if (location.getCity() != null && !location.getCity().isBlank()) address.add(location.getCity());
            if (location.getCountry() != null && !location.getCountry().isBlank()) address.add(location.getCountry());
            if (!address.isEmpty()) {
                sb.append(" — ").append(String.join(", ", address));
            }
            sb.append('\n');
            String hours = renderHours(location.getOperatingHours());
            if (!hours.isEmpty()) {
                sb.append("    hours: ").append(hours).append('\n');
            }
        }
    }

    /** Opening hours in weekday order, so "are you open on Friday?" is answerable at a glance. */
    private static String renderHours(List<OperatingHoursResponse> hours) {
        if (hours == null || hours.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        hours.stream()
                .filter(h -> h.getDayOfWeek() != null)
                .sorted(Comparator.comparing(OperatingHoursResponse::getDayOfWeek))
                .forEach(h -> {
                    DayOfWeek day = h.getDayOfWeek();
                    String label = day.name().charAt(0) + day.name().substring(1, 3).toLowerCase();
                    if (Boolean.TRUE.equals(h.getIsClosed()) || h.getOpenTime() == null || h.getCloseTime() == null) {
                        parts.add(label + " closed");
                    } else {
                        parts.add(label + " " + h.getOpenTime() + "-" + h.getCloseTime());
                    }
                });
        return String.join(", ", parts);
    }

    // =========================================================================
    // Knowledge base loading
    // =========================================================================

    /**
     * External override first — the same pattern as {@code repair.pricing.path}, so an answer can be
     * corrected on the server without cutting a release. Falls back to the bundled file.
     */
    private static String loadKnowledge(String knowledgePath) {
        if (knowledgePath != null && !knowledgePath.isBlank()) {
            try {
                String external = Files.readString(Path.of(knowledgePath.trim()));
                log.info("[ASSISTANT] Loaded the company knowledge base from {} ({} chars).",
                        knowledgePath, external.length());
                return external;
            } catch (Exception e) {
                log.error("[ASSISTANT] Could not read assistant.knowledge.path={}; falling back to "
                        + "the bundled knowledge base.", knowledgePath, e);
            }
        }
        try (InputStream in = AssistantCompanyService.class.getResourceAsStream("/assistant-knowledge.md")) {
            if (in == null) {
                log.warn("[ASSISTANT] No bundled assistant-knowledge.md found — the assistant will "
                        + "only be able to answer from live catalog and store data.");
                return "";
            }
            String bundled = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            log.info("[ASSISTANT] Loaded the bundled company knowledge base ({} chars).", bundled.length());
            return bundled;
        } catch (Exception e) {
            log.error("[ASSISTANT] Could not read the bundled assistant-knowledge.md.", e);
            return "";
        }
    }

    private static <T> List<T> unwrapList(ResponseEntity<ApiResponse<List<T>>> response) {
        ApiResponse<List<T>> body = response == null ? null : response.getBody();
        List<T> data = body == null ? null : body.getData();
        return data == null ? List.of() : data;
    }

    private static String nullSafe(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}
