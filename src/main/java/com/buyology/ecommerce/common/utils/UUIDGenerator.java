package com.buyology.ecommerce.common.utils;

import java.util.UUID;

public class UUIDGenerator {
    // Private constructor to prevent instantiation
    private UUIDGenerator() {
    }

    /**
     * Generates a random UUID (version 4)
     *
     * @return UUID as String
     */
    
    public static String generateUUID() {
        return UUID.randomUUID().toString();
    }
}