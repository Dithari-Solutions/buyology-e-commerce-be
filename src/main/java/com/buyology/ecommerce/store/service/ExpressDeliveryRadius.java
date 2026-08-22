package com.buyology.ecommerce.store.service;

/**
 * The one radius that defines "30-minute delivery is possible from this store".
 *
 * <p>A constant with its own file because three places consult it — the store delivery-info
 * endpoint, the cart's nearby-store set, and the checkout's express-stores endpoint — and the order
 * resolver's verdict is only as consistent as the number they all share. Two radii drifting apart
 * is a customer quoted express by one endpoint and downgraded by another.
 */
public final class ExpressDeliveryRadius {

    /** Roughly what a courier covers in 30 minutes of Dubai traffic. */
    public static final double KM = 12.5;

    private ExpressDeliveryRadius() {
    }
}
