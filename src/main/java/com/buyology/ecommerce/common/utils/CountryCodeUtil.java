package com.buyology.ecommerce.common.utils;

import java.util.Map;
import java.util.Set;

public final class CountryCodeUtil {

    private CountryCodeUtil() {}

    private static final Map<String, Set<String>> EQUIVALENTS = Map.ofEntries(
            Map.entry("AZ", Set.of("AZ", "AZE")),
            Map.entry("AZE", Set.of("AZ", "AZE")),
            Map.entry("AE", Set.of("AE", "UAE", "ARE")),
            Map.entry("UAE", Set.of("AE", "UAE", "ARE")),
            Map.entry("ARE", Set.of("AE", "UAE", "ARE")),
            Map.entry("SA", Set.of("SA", "SAU")),
            Map.entry("SAU", Set.of("SA", "SAU")),
            Map.entry("TR", Set.of("TR", "TUR")),
            Map.entry("TUR", Set.of("TR", "TUR")),
            Map.entry("EG", Set.of("EG", "EGY")),
            Map.entry("EGY", Set.of("EG", "EGY"))
    );

    public static boolean isSameCountry(String code1, String code2) {
        if (code1 == null || code2 == null) return false;
        String c1 = code1.trim().toUpperCase();
        String c2 = code2.trim().toUpperCase();
        if (c1.equals(c2)) return true;
        Set<String> eq = EQUIVALENTS.get(c1);
        return eq != null && eq.contains(c2);
    }
}
