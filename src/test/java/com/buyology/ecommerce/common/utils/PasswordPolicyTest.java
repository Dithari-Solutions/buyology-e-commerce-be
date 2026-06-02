package com.buyology.ecommerce.common.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Locks in the password-strength rules so they cannot silently regress. */
class PasswordPolicyTest {

    @Test
    void acceptsAStrongPassword() {
        assertDoesNotThrow(() -> PasswordPolicy.validate("Str0ngPass"));
        assertDoesNotThrow(() -> PasswordPolicy.validate("Aa1aaaaa"));
    }

    @Test
    void rejectsNullOrEmpty() {
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate(null));
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate(""));
    }

    @Test
    void rejectsTooShort() {
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate("Aa1aaa")); // 6 chars
    }

    @Test
    void rejectsMissingCharacterClasses() {
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate("alllowercase1")); // no upper
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate("ALLUPPERCASE1")); // no lower
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate("NoDigitsHere"));  // no digit
    }

    @Test
    void rejectsCommonWeakPasswords() {
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate("password123"));
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate("Password123")); // case-insensitive
        assertThrows(IllegalArgumentException.class, () -> PasswordPolicy.validate("qwerty123"));
    }
}
