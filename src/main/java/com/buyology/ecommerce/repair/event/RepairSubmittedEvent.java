package com.buyology.ecommerce.repair.event;

import java.util.UUID;

/**
 * Published once a repair request is persisted. Consumed AFTER_COMMIT so listeners
 * (currently the AI price estimate) can safely re-load the row from another thread.
 */
public record RepairSubmittedEvent(UUID repairId) {
}
