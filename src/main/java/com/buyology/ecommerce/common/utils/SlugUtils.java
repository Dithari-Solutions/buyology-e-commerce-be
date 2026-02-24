package com.buyology.ecommerce.common.utils;

import java.util.Map;

public class SlugUtils {

    private static final Map<Character, String> ARABIC_TO_LATIN = Map.ofEntries(
            Map.entry('ا', "a"),
            Map.entry('أ', "a"),
            Map.entry('إ', "i"),
            Map.entry('آ', "aa"),
            Map.entry('ب', "b"),
            Map.entry('ت', "t"),
            Map.entry('ث', "th"),
            Map.entry('ج', "j"),
            Map.entry('ح', "h"),
            Map.entry('خ', "kh"),
            Map.entry('د', "d"),
            Map.entry('ذ', "dh"),
            Map.entry('ر', "r"),
            Map.entry('ز', "z"),
            Map.entry('س', "s"),
            Map.entry('ش', "sh"),
            Map.entry('ص', "s"),
            Map.entry('ض', "d"),
            Map.entry('ط', "t"),
            Map.entry('ظ', "z"),
            Map.entry('ع', "a"),
            Map.entry('غ', "gh"),
            Map.entry('ف', "f"),
            Map.entry('ق', "q"),
            Map.entry('ك', "k"),
            Map.entry('ل', "l"),
            Map.entry('م', "m"),
            Map.entry('ن', "n"),
            Map.entry('ه', "h"),
            Map.entry('و', "w"),
            Map.entry('ي', "y"),
            Map.entry('ة', "a"),
            Map.entry('ى', "a"),
            Map.entry('ؤ', "w"),
            Map.entry('ئ', "y"),
            // Diacritics — strip
            Map.entry('\u064B', ""),
            Map.entry('\u064C', ""),
            Map.entry('\u064D', ""),
            Map.entry('\u064E', ""),
            Map.entry('\u064F', ""),
            Map.entry('\u0650', ""),
            Map.entry('\u0651', ""),
            Map.entry('\u0652', "")
    );

    private SlugUtils() {
    }

    /**
     * Converts an Arabic (or mixed) string into a URL-safe ASCII slug.
     * Arabic letters are transliterated to their Latin equivalents.
     * Spaces become hyphens. Any remaining non-alphanumeric characters are dropped.
     *
     * Example: "إلكترونيات" → "ilktrwnyat"
     */
    public static String toSlug(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (char c : input.toCharArray()) {
            if (ARABIC_TO_LATIN.containsKey(c)) {
                sb.append(ARABIC_TO_LATIN.get(c));
            } else if (c == ' ' || c == '-' || c == '_') {
                sb.append('-');
            } else if (Character.isLetterOrDigit(c) && c < 128) {
                sb.append(Character.toLowerCase(c));
            }
        }

        return sb.toString()
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
    }
}
