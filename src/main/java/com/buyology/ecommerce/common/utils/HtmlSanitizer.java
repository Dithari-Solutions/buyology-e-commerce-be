package com.buyology.ecommerce.common.utils;

import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import org.jsoup.safety.Safelist;

/**
 * Stored-XSS defense for user-generated free text (chat messages, reviews, questions,
 * ratings, supplier/product copy). These fields are plain text, never rich HTML, so we
 * strip ALL markup and return the decoded plain-text content. Applying this on the write
 * path means a malicious payload can never reach a consumer that renders it as HTML.
 */
public final class HtmlSanitizer {

    private HtmlSanitizer() {
    }

    /**
     * Removes every HTML tag/attribute and returns the plain-text content with entities
     * decoded back to their literal characters. Null-safe; preserves leading/trailing
     * whitespace semantics of the caller (does not trim).
     */
    public static String stripHtml(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        // Safelist.none() drops all tags; Jsoup escapes the surviving text, so decode it
        // back to plain text (e.g. "Tom &amp; Jerry" -> "Tom & Jerry").
        String cleaned = Jsoup.clean(input, Safelist.none());
        return Parser.unescapeEntities(cleaned, false);
    }
}
