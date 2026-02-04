package com.buyology.ecommerce.common.utils;

import java.util.regex.Pattern;

public final class EmailValidation {

    // RFC 5322–inspired, practical (not insane) email regex
    private static final String EMAIL_REGEX = "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$";

    private static final Pattern EMAIL_PATTERN = Pattern.compile(EMAIL_REGEX, Pattern.CASE_INSENSITIVE);

    // Private constructor → prevent instantiation
    private EmailValidation() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Validates if email format is correct
     */
    public static boolean isValid(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }

        return EMAIL_PATTERN.matcher(email).matches();
    }

    /**
     * Same as isValid but throws exception (use in services)
     */
    public static void validateOrThrow(String email) {
        if (!isValid(email)) {
            throw new IllegalArgumentException("Invalid email address: " + email);
        }
    }
}
