package com.buyology.ecommerce.common.utils;

import java.time.ZoneId;

/**
 * The shop's own timezone: the one a calendar date typed into the dashboard means.
 *
 * <p>One constant because the literal was already in three places and the flash-sale window made it
 * four — and the four were not decorative. The analytics day, the quick-delivery opening-hours check
 * and now the instant a sale ends are all cut on this zone; two of them disagreeing would move a
 * business day, or end a sale four hours early, with nothing in the code to show which copy was
 * meant to be authoritative.
 *
 * <p>Not configurable. A per-deployment zone would mean a sale created by an admin in one region
 * ending at a different moment than the one who reads the dashboard expects, and every stored
 * instant would then need its zone stored beside it. The business is in the UAE; when that stops
 * being true this constant is the one place that has to change.
 */
public final class BusinessZone {

    /** Asia/Dubai. */
    public static final ZoneId ID = ZoneId.of("Asia/Dubai");

    private BusinessZone() {
    }
}
