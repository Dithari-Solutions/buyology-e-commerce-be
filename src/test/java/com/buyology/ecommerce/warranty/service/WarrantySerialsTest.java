package com.buyology.ecommerce.warranty.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The serial is the only key to a public lookup and is read off a printed card, so its shape and
 * its forgiveness of typing are the whole contract.
 */
class WarrantySerialsTest {

    @Test
    void generatesTheDocumentedShapeFromAnAlphabetWithoutLookAlikes() {
        for (int i = 0; i < 2_000; i++) {
            String serial = WarrantySerials.generate();
            assertTrue(serial.matches("DTH-[0-9A-Z]{4}-[0-9A-Z]{4}-[0-9A-Z]{4}"), serial);
            assertFalse(serial.substring(4).matches(".*[ILOU].*"), "no I, L, O or U: " + serial);
        }
    }

    @Test
    void doesNotRepeatAcrossManyDraws() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 20_000; i++) {
            assertTrue(seen.add(WarrantySerials.generate()), "60 random bits should not collide here");
        }
    }

    @Test
    void generationIsDrivenByTheRandomSourceItIsGiven() {
        assertEquals(WarrantySerials.generate(new Random(42)), WarrantySerials.generate(new Random(42)));
    }

    @Test
    void findsTheCardWhateverCaseSpacingOrDashesThePersonTyped() {
        String canonical = "DTH-7K4Q-2M9X-QH3T";
        assertEquals(canonical, WarrantySerials.canonicalize("DTH-7K4Q-2M9X-QH3T"));
        assertEquals(canonical, WarrantySerials.canonicalize("  dth-7k4q-2m9x-qh3t "));
        assertEquals(canonical, WarrantySerials.canonicalize("DTH7K4Q2M9XQH3T"));
        assertEquals(canonical, WarrantySerials.canonicalize("dth 7k4q 2m9x qh3t"));
    }

    @Test
    void readsLookAlikeLettersAsTheDigitsTheyCanOnlyBe() {
        assertEquals("DTH-0000-1111-2345", WarrantySerials.canonicalize("DTH-OoOo-IiLl-2345"));
    }

    @Test
    void leavesThePrefixAloneWhenMappingLookAlikes() {
        // Only the twelve random characters are base32; "DTH" is never rewritten.
        assertEquals("DTH-0ABC-DEFG-HJKM", WarrantySerials.canonicalize("dth-oabc-defg-hjkm"));
    }

    @Test
    void anythingNotShapedLikeOurSerialIsPassedOnCleanedAndSimplyWillNotMatch() {
        assertEquals("ABC123", WarrantySerials.canonicalize("abc-123"));
        assertEquals("DTH12345", WarrantySerials.canonicalize("DTH-12345"));
    }

    @Test
    void nothingToLookUpIsNull() {
        assertNull(WarrantySerials.canonicalize(null));
        assertNull(WarrantySerials.canonicalize("   "));
        assertNull(WarrantySerials.canonicalize("---"));
        assertNull(WarrantySerials.canonicalize("A".repeat(101)), "absurd input is not even cleaned");
    }
}
