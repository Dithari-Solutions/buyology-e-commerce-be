package com.buyology.ecommerce.common.utils;

import java.util.Set;

/**
 * Reusable password policy enforcement.
 *
 * Policy:
 *   - minimum 8 characters
 *   - at least one uppercase letter
 *   - at least one lowercase letter
 *   - at least one digit
 *   - must not be one of the most common weak passwords (denylist)
 *
 * Special characters are intentionally NOT required to avoid over-friction.
 */
public final class PasswordPolicy {

    private static final int MIN_LENGTH = 8;

    // Small hardcoded denylist of the most common weak passwords.
    // Compared case-insensitively.
    private static final Set<String> DENYLIST = Set.of(
            "password",
            "password1",
            "password123",
            "passw0rd",
            "12345678",
            "123456789",
            "1234567890",
            "qwerty123",
            "qwertyuiop",
            "abc12345",
            "iloveyou",
            "admin123",
            "welcome1",
            "letmein123"
    );

    private PasswordPolicy() {
        // utility class
    }

    /**
     * Validates a password against the policy.
     *
     * @param password the plain-text password to validate
     * @throws IllegalArgumentException with a clear, user-facing message if the password fails policy
     */
    public static void validate(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password is required");
        }

        if (password.length() < MIN_LENGTH) {
            throw new IllegalArgumentException("Password must be at least " + MIN_LENGTH + " characters");
        }

        boolean hasUppercase = false;
        boolean hasLowercase = false;
        boolean hasDigit = false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (Character.isUpperCase(c)) {
                hasUppercase = true;
            } else if (Character.isLowerCase(c)) {
                hasLowercase = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            }
        }

        if (!hasUppercase || !hasLowercase || !hasDigit) {
            throw new IllegalArgumentException(
                    "Password must contain at least one uppercase letter, one lowercase letter, and one number");
        }

        if (DENYLIST.contains(password.toLowerCase())) {
            throw new IllegalArgumentException("Password is too common. Please choose a stronger password");
        }
    }
}
