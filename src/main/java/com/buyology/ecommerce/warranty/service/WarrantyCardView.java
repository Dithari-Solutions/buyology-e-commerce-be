package com.buyology.ecommerce.warranty.service;

import com.buyology.ecommerce.warranty.domain.WarrantyCard;
import com.buyology.ecommerce.warranty.domain.WarrantyCardStatus;
import com.buyology.ecommerce.warranty.dto.OrderItemWarranty;

import java.time.LocalDate;

/** How a stored card reads on a given day. Shared by the public lookup and the order pages. */
final class WarrantyCardView {

    /** Inside this many days of the end date a live card reads EXPIRING_SOON. */
    static final int EXPIRING_SOON_DAYS = 30;

    private WarrantyCardView() {
    }

    /**
     * ACTIVE, EXPIRING_SOON, EXPIRED or VOID. Expiry is computed rather than stored so no job has
     * to flip cards at midnight; the end date is the last covered day.
     */
    static String status(WarrantyCard card, LocalDate today) {
        if (card.getStatus() == WarrantyCardStatus.VOID) return "VOID";
        if (today.isAfter(card.getEndsOn())) return "EXPIRED";
        if (!today.plusDays(EXPIRING_SOON_DAYS).isBefore(card.getEndsOn())) return "EXPIRING_SOON";
        return "ACTIVE";
    }

    static OrderItemWarranty forOrderItem(WarrantyCard card, LocalDate today) {
        return new OrderItemWarranty(
                card.getSerialNumber(),
                status(card, today),
                card.getStartsOn().toString(),
                card.getEndsOn().toString());
    }
}
