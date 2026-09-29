package com.buyology.ecommerce.order.service;

import com.buyology.ecommerce.order.dto.CancellationFeedback;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The only thing allowed to turn a customer's cancellation questionnaire into the document stored in
 * {@code orders.cancellation_feedback}, and the only thing that reads it back.
 *
 * <p>Everything here exists because of who writes this column. It is not an admin field: anyone who
 * can cancel their own order can put arbitrary strings in it, from a client we do not control, and it
 * lands in a JSONB column that the dashboard then renders. So the client's document is never stored
 * — it is rebuilt here from cleaned, capped values, with {@code version} and {@code submittedAt}
 * stamped by this server rather than taken from the request. A client that lies about when it
 * submitted, or sends a 4 MB answer, changes nothing except what gets thrown away.
 *
 * <p>Every cap truncates and never rejects. The customer's cancellation is the half that matters;
 * the analytics is the nice-to-have. A shopper must never be unable to cancel an order because they
 * typed a long sentence, so there is no input on this path that produces an error — only a shorter
 * stored document, or none at all.
 *
 * <p>It owns its own {@link ObjectMapper} rather than taking the application bean, deliberately.
 * This is a stored, queried format shared with the dashboard, so its date shape must not change
 * because someone adjusts Jackson configuration elsewhere in the application.
 */
final class CancellationFeedbackCodec {

    private static final Logger log = LoggerFactory.getLogger(CancellationFeedbackCodec.class);

    /** Bumped only when the stored shape changes in a way a reader has to branch on. */
    static final int VERSION = 1;

    // The per-field ceilings. Generous enough that no honest answer is ever clipped — the longest
    // real question in the flow is ~60 characters and the free-text answer box is a single line —
    // and small enough that twelve of them cannot make a row the dashboard chokes on.
    static final int MAX_ANSWERS = 12;
    static final int KEY_MAX = 60;
    static final int CODE_MAX = 60;
    static final int QUESTION_MAX = 300;
    static final int ANSWER_MAX = 600;

    /**
     * Hard ceiling on the whole serialized document.
     *
     * <p>The per-field caps bound it at roughly 13 KB in the worst case, which is a fine JSONB value
     * and a poor API response field to repeat in a list of orders. Trailing answers are dropped
     * until it fits, because a truncated JSON string is not a smaller document — it is an unreadable
     * one, and Postgres would reject it and take the cancellation down with it.
     */
    static final int MAX_JSON_CHARS = 8000;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            // A document written by a newer deploy must still be readable by an older one during a
            // rolling restart. An unknown field is not a reason to make an order unviewable.
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private CancellationFeedbackCodec() {}

    /**
     * Cleans, caps and serializes what the client sent, stamping the server's own version and time.
     *
     * @return the JSON to store, or null when there is nothing worth storing — no feedback, no
     *         usable answers left after cleaning, or a document that could not be serialized. Null
     *         is the correct stored value in every one of those cases; {@code "{}"} would claim a
     *         questionnaire was answered with nothing in it.
     */
    static String toStoredJson(CancellationFeedback raw) {
        return toStoredJson(raw, Instant.now());
    }

    /**
     * Same, from the raw JSON the client posted.
     *
     * <p>The request carries an unbound {@link JsonNode} on purpose, so that a payload Jackson would
     * have refused reaches here instead of becoming an HTTP 400 the shopper cannot get past. This is
     * where that promise is kept: a node of the wrong shape entirely — {@code "answers":"none"}, an
     * unparseable {@code submittedAt}, a string where an object belongs — converts to nothing and
     * stores nothing, and the cancellation carries on without it.
     *
     * <p>Converted with this class's own lenient mapper rather than the application bean, so an
     * unknown key is ignored here exactly as it is when reading a document back.
     */
    static String toStoredJson(JsonNode raw) {
        if (raw == null || raw.isNull() || !raw.isObject()) return null;
        CancellationFeedback converted;
        try {
            converted = MAPPER.convertValue(raw, CancellationFeedback.class);
        } catch (Exception e) {
            log.warn("[ORDER] Cancellation feedback was not the shape we expect, storing none: {}",
                    e.getMessage());
            return null;
        }
        return toStoredJson(converted);
    }

    /**
     * Seam for the tests only — production always stamps {@link Instant#now()}. Package-private so
     * no caller outside this package can choose the submission time, which is the whole point of
     * ignoring the client's.
     */
    static String toStoredJson(CancellationFeedback raw, Instant submittedAt) {
        if (raw == null) return null;

        List<CancellationFeedback.Answer> kept = cleanAnswers(raw.answers());
        if (kept.isEmpty()) return null;

        String source = coerceSource(raw.source());
        String reasonCode = clean(raw.reasonCode(), CODE_MAX);
        // Truncated to the second: nobody groups cancellations by millisecond, and it keeps the
        // stored document exactly the shape the clients were written against.
        Instant stamped = submittedAt.truncatedTo(ChronoUnit.SECONDS);

        while (!kept.isEmpty()) {
            String json = write(new CancellationFeedback(VERSION, source, reasonCode, stamped, kept));
            if (json == null) return null;
            if (json.length() <= MAX_JSON_CHARS) return json;
            kept.remove(kept.size() - 1);
        }

        // Only reachable if a single cleaned answer still overflows, which the per-field caps make
        // arithmetically impossible — logged rather than asserted because the cancellation must go
        // through either way.
        log.warn("[ORDER] Cancellation feedback exceeded {} chars with every answer dropped; storing none",
                MAX_JSON_CHARS);
        return null;
    }

    /**
     * Reads a stored document back for the API response.
     *
     * <p>Degrades to null on anything it cannot parse instead of throwing. A bad value in this
     * column — hand-edited, written by a future version, corrupted — must never be able to stop an
     * order from being viewed; the order is the record, the feedback is a note attached to it.
     */
    static CancellationFeedback fromStoredJson(String stored) {
        if (stored == null || stored.isBlank()) return null;
        try {
            CancellationFeedback parsed = MAPPER.readValue(stored, CancellationFeedback.class);
            if (parsed == null || parsed.answers() == null || parsed.answers().isEmpty()) return null;
            return parsed;
        } catch (Exception e) {
            log.warn("[ORDER] Unreadable cancellation_feedback, serving the order without it: {}",
                    e.getMessage());
            return null;
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private static List<CancellationFeedback.Answer> cleanAnswers(List<CancellationFeedback.Answer> raw) {
        List<CancellationFeedback.Answer> kept = new ArrayList<>();
        if (raw == null) return kept;

        for (CancellationFeedback.Answer a : raw) {
            if (kept.size() == MAX_ANSWERS) break; // extra answers are dropped, not an error
            if (a == null) continue;

            String answer = clean(a.answer(), ANSWER_MAX);
            // An unanswered question is not data. Storing it would put empty rows in the dashboard's
            // list and count as a response in the analytics it exists to feed.
            if (answer == null) continue;

            kept.add(new CancellationFeedback.Answer(
                    clean(a.key(), KEY_MAX),
                    clean(a.question(), QUESTION_MAX),
                    answer,
                    clean(a.code(), CODE_MAX)));
        }
        return kept;
    }

    /** WEB or MOBILE, or null. Anything else is a client we do not know, and naming it proves nothing. */
    private static String coerceSource(String raw) {
        String cleaned = clean(raw, 20);
        if (cleaned == null) return null;
        String upper = cleaned.toUpperCase(Locale.ROOT);
        return "WEB".equals(upper) || "MOBILE".equals(upper) ? upper : null;
    }

    /**
     * One pass for every text value: control characters out, every run of whitespace (newlines and
     * tabs included) down to a single space, trimmed, then cut to {@code max}.
     *
     * <p>The newline collapse is not cosmetic. These strings are rendered as list rows in the
     * dashboard and are read in log lines and exports, where an embedded newline turns one answer
     * into what looks like several records.
     *
     * @return the cleaned value, or null when nothing is left — so blank and absent are one case
     *         everywhere downstream
     */
    private static String clean(String value, int max) {
        if (value == null) return null;

        StringBuilder out = new StringBuilder(Math.min(value.length(), max));
        boolean spacePending = false;
        // Iterating by CODE POINT, not by char. An emoji is two chars in Java, so a char-indexed loop
        // that stops at `max` can stop between the two halves of one, and half a surrogate pair is
        // not a character: it cannot be encoded to UTF-8, which turns a merely long answer into a
        // value the driver cannot send. Stepping a whole code point at a time means the cut always
        // lands between characters, at the cost of a document a char or two under the cap.
        for (int i = 0; i < value.length(); ) {
            int cp = value.codePointAt(i);
            int width = Character.charCount(cp);
            if (out.length() + width > max) break;
            i += width;

            if (Character.isWhitespace(cp)) {
                spacePending = !out.isEmpty(); // never leading
                continue;
            }
            // Control characters go, and so do the invisible formatting ones. Zero-width joiners and
            // bidi overrides survive isISOControl, and this text is rendered into an admin's browser
            // and into CSV exports: a right-to-left override in a customer's free text reverses the
            // rest of the line on screen, so what an admin reads is not what was submitted.
            if (Character.isISOControl(cp) || isInvisibleFormatting(cp)) continue;
            if (spacePending) {
                out.append(' ');
                spacePending = false;
                if (out.length() + width > max) break;
            }
            out.appendCodePoint(cp);
        }
        return out.isEmpty() ? null : out.toString();
    }

    /**
     * Zero-width and direction-controlling characters, which are invisible but not control characters.
     *
     * <p>Named ranges rather than a character class because the point is what they do when displayed,
     * not what Unicode calls them: bidi embedding and override marks (U+202A-U+202E), isolates
     * (U+2066-U+2069), the zero-width set and directional marks (U+200B-U+200F), and the byte-order
     * mark (U+FEFF), which turns up at the front of text pasted from a spreadsheet.
     */
    private static boolean isInvisibleFormatting(int cp) {
        return (cp >= 0x200B && cp <= 0x200F)
                || (cp >= 0x202A && cp <= 0x202E)
                || (cp >= 0x2066 && cp <= 0x2069)
                || cp == 0xFEFF;
    }

    private static String write(CancellationFeedback feedback) {
        try {
            return MAPPER.writeValueAsString(feedback);
        } catch (Exception e) {
            // Never fatal. The cancellation is the important half and it has already been decided;
            // losing the questionnaire costs one row of analytics.
            log.warn("[ORDER] Could not serialize cancellation feedback, storing none: {}", e.getMessage());
            return null;
        }
    }
}
