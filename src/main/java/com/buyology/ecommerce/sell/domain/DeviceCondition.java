package com.buyology.ecommerce.sell.domain;

/**
 * Condition the customer grades their own device at. Unlike a repair — where the fault photos say
 * everything that matters — a buy-back price hinges on wear that photos alone under-report
 * (battery health, scratches under a case, whether it still powers on), so the customer declares it
 * up front and both the AI valuation and procurement's inspection are anchored to that claim.
 *
 * The grade is the customer's word, not a verdict: procurement re-grades the device on arrival and
 * the final offer reflects what they actually find.
 */
public enum DeviceCondition {
    /** Barely used, no visible marks, everything works. */
    LIKE_NEW,
    /** Normal signs of use, fully functional. */
    GOOD,
    /** Visible wear or a minor fault, still usable. */
    FAIR,
    /** Heavy damage, or does not power on / work reliably. */
    POOR
}
