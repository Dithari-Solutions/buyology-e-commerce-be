package com.buyology.ecommerce.warranty.service;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Random;

/**
 * Warranty serial numbers: {@code DTH-XXXX-XXXX-XXXX}, twelve random characters from Crockford's
 * base32 alphabet.
 *
 * <p>Random rather than sequential because the lookup is public and the serial is its only key:
 * twelve symbols are 60 bits, so walking the space to harvest other people's cards is hopeless.
 *
 * <p>The alphabet has no I, L, O or U, which is what makes a serial safe to read off a card. An O
 * typed for a zero, or an I or L typed for a one, cannot be some other valid serial, so lookups map
 * them back (Crockford's own decoding rule) instead of answering "not found".
 */
public final class WarrantySerials {

    static final String PREFIX = "DTH";
    static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    static final int RANDOM_LENGTH = 12;

    private static final SecureRandom RANDOM = new SecureRandom();

    private WarrantySerials() {
    }

    public static String generate() {
        return generate(RANDOM);
    }

    static String generate(Random random) {
        StringBuilder serial = new StringBuilder(PREFIX);
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            if (i % 4 == 0) serial.append('-');
            serial.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return serial.toString();
    }

    /**
     * The stored form of what a person typed: upper case, dashes where they belong, look-alike
     * letters mapped to digits. Spaces, missing dashes and lower case all still find the card.
     *
     * @return the canonical serial, the cleaned input if it is not shaped like one of ours (it will
     *         simply not be found), or null when there is nothing to look up
     */
    public static String canonicalize(String raw) {
        if (raw == null || raw.length() > 100) return null;
        String cleaned = raw.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (cleaned.isEmpty()) return null;
        if (cleaned.startsWith(PREFIX) && cleaned.length() == PREFIX.length() + RANDOM_LENGTH) {
            String body = cleaned.substring(PREFIX.length())
                    .replace('O', '0')
                    .replace('I', '1')
                    .replace('L', '1');
            return PREFIX + "-" + body.substring(0, 4) + "-" + body.substring(4, 8) + "-" + body.substring(8);
        }
        return cleaned;
    }
}
