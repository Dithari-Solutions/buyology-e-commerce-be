package com.buyology.ecommerce.productimport.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import com.buyology.ecommerce.productimport.service.SpreadsheetReader.RawRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Turns the free-text column of a supplier's spreadsheet into structured product data, in all
 * three storefront languages.
 *
 * <p>This exists because the sheets are genuinely a mess and a regex cannot be honest about it.
 * One column holds {@code "Lenovo T490 | Intel Core i7 | 8th Gen"} on one line and
 * {@code "Lenovo T490 Core i5 | 8th Gen | 16GB RAM | 256 SSD | 14\" INCH"} on the next, with
 * pipes used inconsistently, units written five ways, and the occasional row that is barely a
 * description at all. A parser can be made to handle the shapes present in today's file, and it
 * will then mis-parse tomorrow's silently.
 *
 * <p><strong>Nothing here writes to the catalogue.</strong> Extraction produces rows for a human
 * to review; {@code ProductImportService} is what creates products, and only after someone has
 * approved what was read. That separation is the point — a misread spec on a refurbished laptop is
 * a customer dispute, so being uncertain has to be cheap and visible rather than silently
 * resolved.
 */
@Service
public class ProductImportAiService {

    private static final Logger log = LoggerFactory.getLogger(ProductImportAiService.class);

    /**
     * Rows per request. Small on purpose: each row produces six pieces of prose (three titles,
     * three descriptions), so a large batch runs into the output cap, and a truncated response
     * loses the whole batch rather than one row.
     */
    private static final int BATCH_SIZE = 5;

    private static final int MAX_TOKENS = 16_000;

    private static final String SYSTEM_PROMPT = """
            You normalise messy supplier spreadsheets into clean catalogue data for an online shop \
            that sells new and refurbished computers in the UAE.

            Each input line is one row of a spreadsheet, written by hand by a supplier. The same \
            machine is written differently from row to row: the separator may be a pipe, a comma, \
            a slash or nothing at all; the order of the specs varies; units are written as "256 \
            SSD", "256GB", "256 GB SSD" or "1 TB"; and some rows are little more than a category, \
            like "MONITOR 22 INCH".

            Your job is to read each row and say what it describes.

            The rules that matter:

            1. NEVER invent a specification. If the row does not state the processor generation, \
            the generation is null — do not infer it from the model number, however confident you \
            feel. A fabricated spec on a refurbished laptop becomes a customer dispute and a \
            return. A null is free.
            2. A row you cannot confidently read is not a failure. Fill in what you can, set \
            confidence to LOW, and say what is unclear in needsReview. A human reads that list.
            3. Titles and descriptions must only state what the row supports. No marketing \
            language, no "fast", no "powerful", no invented battery life, warranty, condition \
            grade or included accessories.
            4. Translate into Azerbaijani and Arabic naturally, but keep brand names, model \
            numbers, and units (GB, TB, SSD, inch sizes) in Latin script exactly as in English. A \
            customer searching "T490" must find it in any language.
            5. Return exactly one entry per input row, with rowNumber copied from the input. Never \
            merge rows, never drop a row, never renumber.
            """;

    private final AnthropicClient client;
    private final String model;

    public ProductImportAiService(
            @Value("${anthropic.api-key:}") String apiKey,
            @Value("${product-import.model:claude-opus-5}") String model) {
        this.model = model;
        AnthropicClient built = null;
        if (apiKey != null && !apiKey.isBlank()) {
            try {
                built = AnthropicOkHttpClient.builder().apiKey(apiKey.trim()).build();
            } catch (RuntimeException e) {
                log.error("[IMPORT] Could not build the Anthropic client — extraction is disabled: {}",
                        e.toString());
            }
        } else {
            log.warn("[IMPORT] anthropic.api-key is not set — product import extraction is disabled.");
        }
        this.client = built;
    }

    public boolean isAvailable() {
        return client != null;
    }

    /**
     * Extracts every row, in batches.
     *
     * <p>A failed batch does not fail the import: those rows simply come back missing from the
     * map, and the caller marks them {@code EXTRACTION_FAILED} for a human to handle. Forty-four
     * good rows and one that needs typing by hand is a far better outcome than refusing all
     * forty-five because one batch timed out.
     *
     * @return extraction by row number; a row absent from the map could not be extracted
     */
    public Map<Integer, SpecExtraction> extractAll(List<RawRow> rows) {
        Map<Integer, SpecExtraction> out = new HashMap<>();
        if (client == null || rows == null || rows.isEmpty()) {
            return out;
        }

        for (int start = 0; start < rows.size(); start += BATCH_SIZE) {
            List<RawRow> batch = rows.subList(start, Math.min(start + BATCH_SIZE, rows.size()));
            try {
                SpecExtractionBatch result = extractBatch(batch);
                if (result == null || result.rows() == null) {
                    log.warn("[IMPORT] Empty extraction for rows {}-{}",
                            batch.get(0).rowNumber(), batch.get(batch.size() - 1).rowNumber());
                    continue;
                }
                for (SpecExtractionBatch.Item item : result.rows()) {
                    if (item == null || item.rowNumber() == null || item.product() == null) {
                        continue;
                    }
                    // Only accept row numbers we actually sent. A hallucinated or renumbered row
                    // would otherwise overwrite a real extraction, and every product after it
                    // would carry another machine's specs with nothing looking wrong.
                    boolean known = batch.stream().anyMatch(r -> r.rowNumber() == item.rowNumber());
                    if (!known) {
                        log.warn("[IMPORT] Discarding extraction for unknown row {}", item.rowNumber());
                        continue;
                    }
                    out.put(item.rowNumber(), item.product());
                }
            } catch (RuntimeException e) {
                // Swallowed per batch, on purpose — see the method comment.
                log.error("[IMPORT] Extraction failed for rows {}-{}: {}",
                        batch.get(0).rowNumber(), batch.get(batch.size() - 1).rowNumber(), e.toString());
            }
        }

        log.info("[IMPORT] Extracted {} of {} row(s)", out.size(), rows.size());
        return out;
    }

    private SpecExtractionBatch extractBatch(List<RawRow> batch) {
        StructuredMessageCreateParams<SpecExtractionBatch> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(MAX_TOKENS)
                .thinking(ThinkingConfigAdaptive.builder().build())
                .system(SYSTEM_PROMPT)
                .addUserMessage(buildPrompt(batch))
                .outputConfig(SpecExtractionBatch.class)
                .build();

        Optional<SpecExtractionBatch> parsed = client.messages().create(params).content().stream()
                .flatMap(block -> block.text().stream())
                .map(text -> text.text())
                .findFirst();
        return parsed.orElse(null);
    }

    private static String buildPrompt(List<RawRow> batch) {
        StringBuilder sb = new StringBuilder();
        sb.append("Read these ").append(batch.size())
          .append(" spreadsheet rows and return one entry for each.\n\n");
        for (RawRow r : batch) {
            sb.append("Row ").append(r.rowNumber()).append(": ").append(r.text());
            if (r.code() != null && !r.code().isBlank()) {
                sb.append("   [supplier code: ").append(r.code()).append(']');
            }
            sb.append('\n');
        }
        sb.append("\nReturn an entry for every row above, with rowNumber matching exactly.");
        return sb.toString();
    }

    /** Convenience for a single row — used by the re-extract action on the review screen. */
    public SpecExtraction extractOne(RawRow row) {
        Map<Integer, SpecExtraction> result = extractAll(List.of(row));
        return result.get(row.rowNumber());
    }

    /** Flattens the model's review list into the one-phrase-per-line form the column stores. */
    public static String joinNeedsReview(SpecExtraction e) {
        if (e == null || e.needsReview() == null || e.needsReview().isEmpty()) {
            return null;
        }
        List<String> cleaned = new ArrayList<>();
        for (String s : e.needsReview()) {
            if (s != null && !s.isBlank()) {
                cleaned.add(s.trim());
            }
        }
        return cleaned.isEmpty() ? null : String.join("\n", cleaned);
    }
}
