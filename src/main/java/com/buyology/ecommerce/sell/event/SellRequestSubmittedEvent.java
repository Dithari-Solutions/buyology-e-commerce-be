package com.buyology.ecommerce.sell.event;

import java.util.UUID;

/**
 * Published once a sell request is persisted. Consumed AFTER_COMMIT so listeners
 * (currently the AI buy-back valuation) can safely re-load the row from another thread.
 */
public record SellRequestSubmittedEvent(UUID sellRequestId) {
}
