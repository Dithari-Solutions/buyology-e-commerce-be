package com.buyology.ecommerce.warranty.service;

import com.buyology.ecommerce.warranty.domain.WarrantyCard;
import com.buyology.ecommerce.warranty.domain.WarrantyCardStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Status is computed from the dates on the day of the lookup, so its boundaries are pinned here. */
class WarrantyCardViewTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 24);

    private static WarrantyCard endingOn(LocalDate endsOn) {
        WarrantyCard card = new WarrantyCard();
        card.setStartsOn(endsOn.minusMonths(12));
        card.setEndsOn(endsOn);
        card.setStatus(WarrantyCardStatus.ACTIVE);
        return card;
    }

    @Test
    void theEndDateIsStillCovered() {
        assertEquals("EXPIRING_SOON", WarrantyCardView.status(endingOn(TODAY), TODAY));
        assertEquals("EXPIRED", WarrantyCardView.status(endingOn(TODAY.minusDays(1)), TODAY));
    }

    @Test
    void expiringSoonIsTheLastThirtyDays() {
        assertEquals("EXPIRING_SOON", WarrantyCardView.status(endingOn(TODAY.plusDays(30)), TODAY));
        assertEquals("ACTIVE", WarrantyCardView.status(endingOn(TODAY.plusDays(31)), TODAY));
    }

    @Test
    void voidWinsOverDates() {
        WarrantyCard card = endingOn(TODAY.plusYears(1));
        card.setStatus(WarrantyCardStatus.VOID);
        assertEquals("VOID", WarrantyCardView.status(card, TODAY));
    }

    @Test
    void orderItemViewCarriesSerialStatusAndIsoDates() {
        WarrantyCard card = endingOn(LocalDate.of(2027, 6, 24));
        card.setSerialNumber("DTH-7K4Q-2M9X-QH3T");
        var view = WarrantyCardView.forOrderItem(card, TODAY);
        assertEquals("DTH-7K4Q-2M9X-QH3T", view.serialNumber());
        assertEquals("ACTIVE", view.status());
        assertEquals("2026-06-24", view.startDate());
        assertEquals("2027-06-24", view.endDate());
    }
}
