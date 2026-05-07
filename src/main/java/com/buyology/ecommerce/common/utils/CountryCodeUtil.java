package com.buyology.ecommerce.common.utils;

import java.util.Map;
import java.util.Set;

public final class CountryCodeUtil {

    private CountryCodeUtil() {}

    private static final Map<String, Set<String>> EQUIVALENTS = Map.of(
            "AZ", Set.of("AZ", "AZE"),
            "AZE", Set.of("AZ", "AZE"),
            "AE", Set.of("AE", "UAE", "ARE"),
            "UAE", Set.of("AE", "UAE", "ARE"),
            "ARE", Set.of("AE", "UAE", "ARE"),
            "SA", Set.of("SA", "SAU"),
            "SAU", Set.of("SA", "SAU"),
            "TR", Set.of("TR", "TUR"),
            "TUR", Set.of("TR", "TUR"),
            "EG", Set.of("EG", "EGY"),
            "EGY", Set.of("EG", "EGY")
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
