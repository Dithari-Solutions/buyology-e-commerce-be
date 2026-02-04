package com.buyology.ecommerce.common.utils;

import org.springframework.security.crypto.bcrypt.BCrypt;

public class PasswordUtils {

    // --------------------------
    // Hash a plain password
    // --------------------------
    public static String hashPassword(String plainPassword) {
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt());
    }

    // --------------------------
    // Verify a password
    // --------------------------
    public static boolean verifyPassword(String plainPassword, String hashedPassword) {
        return BCrypt.checkpw(plainPassword, hashedPassword);
    }
}
