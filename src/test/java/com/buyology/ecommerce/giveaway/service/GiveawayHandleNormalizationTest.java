package com.buyology.ecommerce.giveaway.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the one rule the fairness of the draw rests on.
 *
 * The uniqueness constraint is on the NORMALISED handle, so normalisation is the whole
 * anti-abuse mechanism: every spelling of the same Instagram profile must collapse to the
 * same string, or one person enters as many times as they can think of ways to type their
 * own username. Each case below is a way somebody would actually paste it.
 */
class GiveawayHandleNormalizationTest {

    private static String norm(String raw) {
        return GiveawayService.normalizeHandle(raw);
    }

    @Test
    void everySpellingOfTheSameProfileCollapsesToOneHandle() {
        String expected = "buyology.online";
        for (String input : new String[]{
                "buyology.online",
                "Buyology.Online",
                "  buyology.online  ",
                "@buyology.online",
                "@Buyology.Online",
                "instagram.com/buyology.online",
                "www.instagram.com/buyology.online",
                "https://instagram.com/buyology.online",
                "https://www.instagram.com/buyology.online/",
                "https://www.instagram.com/buyology.online/?hl=en",
                "INSTAGRAM.COM/buyology.online",
        }) {
            assertEquals(expected, norm(input), "should normalise: " + input);
        }
    }

    @Test
    void rejectsWhatCannotBeAnInstagramUsername() {
        // Rejected input means the entry form says so; it must never become a stored handle,
        // because a junk handle is a free second entry.
        assertNull(norm(null));
        assertNull(norm(""));
        assertNull(norm("   "));
        assertNull(norm("@"));
        assertNull(norm("two words"));
        assertNull(norm("has-a-hyphen"));
        assertNull(norm("email@example.com"));
        assertNull(norm("a".repeat(31)), "Instagram caps usernames at 30 characters");
    }

    @Test
    void acceptsTheFullRangeInstagramItselfAllows() {
        assertEquals("a", norm("a"));
        assertEquals("a".repeat(30), norm("a".repeat(30)));
        assertEquals("under_score.9", norm("Under_Score.9"));
    }

    @Test
    void doesNotTreatAProfileUrlPathAsPartOfTheHandle() {
        // A trailing path segment would otherwise smuggle a distinct string past uniqueness.
        assertNull(norm("instagram.com/buyology.online/tagged"));
    }
}
