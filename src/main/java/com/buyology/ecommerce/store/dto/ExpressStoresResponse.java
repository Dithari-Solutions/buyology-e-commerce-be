package com.buyology.ecommerce.store.dto;

import java.util.List;
import java.util.UUID;

/** The stores that can 30-minute-deliver to a point, and the radius that decided it. */
public record ExpressStoresResponse(double radiusKm, List<UUID> storeIds) {
}
