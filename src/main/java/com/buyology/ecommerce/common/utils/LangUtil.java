package com.buyology.ecommerce.common.utils;

import com.buyology.ecommerce.common.enums.Language;

public class LangUtil {

    private static final Language DEFAULT_LANGUAGE = Language.AZ;

    private LangUtil() {
    }

    /**
     * Resolves the language from a request param or Accept-Language header value.
     * Priority: param > header. Falls back to AZ if neither is valid.
     */
    public static Language resolve(String param, String acceptLanguageHeader) {
        Language fromParam = parse(param);
        if (fromParam != null) {
            return fromParam;
        }
        Language fromHeader = parseAcceptLanguage(acceptLanguageHeader);
        if (fromHeader != null) {
            return fromHeader;
        }
        return DEFAULT_LANGUAGE;
    }

    private static Language parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Language.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Language parseAcceptLanguage(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        // Take the first language tag (e.g. "az", "en-US,en;q=0.9" -> "en")
        String primary = header.split(",")[0].split(";")[0].trim();
        String langCode = primary.split("-")[0].trim();
        return parse(langCode);
    }
}
