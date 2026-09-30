package com.buyology.ecommerce.order.service;

import com.buyology.ecommerce.cart.service.CartLinePricing;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Decides what a checkout should do when a basket's live prices no longer match the ones stamped on
 * its lines.
 *
 * <p>The rule is ASYMMETRIC, and that is the whole point of the class:
 *
 * <ul>
 *   <li><b>Every line cheaper or unchanged — charge it.</b> A sale that started while the customer
 *       was filling in their address is good news; stopping them to say so is a 409 no released
 *       client handles, which turns "you are paying less" into "you cannot buy this". The lines are
 *       corrected and the order is priced from the new figures, silently.</li>
 *   <li><b>ANY line dearer — refuse.</b> That is the one case where carrying on means charging more
 *       than the screen said, which is the exact failure the flash sale must not introduce. The
 *       customer is sent back to a basket that will show them the new prices (the cart re-prices on
 *       every read) and asked to confirm.</li>
 * </ul>
 *
 * <p><b>Judged PER LINE, not on the total</b>, and the difference is money. Summing both sides first
 * lets a rise be paid for by an unrelated drop: one line up 50 and another down 50 nets to zero, so
 * the basket passed with no 409 and the customer was charged above the displayed price on that one
 * line, with nothing in the order recording that it had happened. Netting is not a concession the
 * customer agreed to — they were quoted each line, not a total — and it is invisible, which is worse
 * than the overcharge. So one line getting dearer refuses the whole checkout however cheap the rest
 * became.
 *
 * <p>The converse is deliberately NOT symmetrical: a basket where every change is a drop passes
 * silently, however large the drop. There is no customer interest in being stopped to be told they
 * are paying less.
 *
 * <p><b>All of that is the rule BEFORE the money is captured.</b> This class only measures and
 * classifies; who may act on the measurement is the caller's question, and on the cart-first payment
 * flow the answer is nobody — the gateway has already captured the quoted total, so the quote is
 * authoritative and a difference is recorded as an anomaly rather than refused or applied. See
 * {@code OrderService.CapturePhase}. A refusal reached after capture does not protect the customer
 * from anything; it destroys the order their money has already paid for.
 *
 * <p>Deliberately free of Spring, repositories and the clock — like {@link CheckoutIdentity}, which
 * it sits next to in createOrder — so the decision is testable without constructing OrderService.
 */
final class CheckoutRepricing {

    private CheckoutRepricing() {
    }

    /**
     * One basket line, as stamped and as it prices now.
     *
     * @param stamped   the unit price the line carries — what the customer was quoted for it
     * @param stampedOriginal the "was" figure the line carries, or null when it was not on a sale
     * @param live      the unit price the live listing gives, or null when there is no listing to price
     *                  from because the assignment has been switched off. Null is NOT a price of zero:
     *                  such a line contributes its stamped price to both totals and is never counted
     *                  as risen, so availability problems stay availability problems.
     * @param liveOriginal the "was" price to strike through alongside {@code live}, or null
     */
    record Line(UUID cartItemId, String productSku, int quantity,
                BigDecimal stamped, BigDecimal stampedOriginal,
                BigDecimal live, BigDecimal liveOriginal) {
    }

    /**
     * What the basket cost, what it costs now, which lines differ, and which of those ROSE.
     *
     * <p>{@code risen} is a subset of {@code moved} and is what the decision is made on. The two
     * totals are carried for the log line and the 409's message — a shopper being asked to
     * re-confirm wants to see the two figures — and are deliberately NOT what decides anything.
     */
    record Outcome(BigDecimal stampedTotal, BigDecimal liveTotal, List<Line> moved, List<Line> risen) {

        boolean anythingMoved() {
            return !moved.isEmpty();
        }

        /**
         * True when ANY line would be charged MORE than the basket showed for it.
         *
         * <p>Per line, not on the totals. Compared on the totals, a line up 50 and a line down 50
         * cancel and the customer is quietly overcharged on the first one.
         */
        boolean dearer() {
            return !risen.isEmpty();
        }

        /** The line that rose the most, for the log line and the "which item" in the refusal. */
        Line largestRise() {
            Line worst = null;
            BigDecimal worstBy = null;
            for (Line line : risen) {
                BigDecimal by = line.live().subtract(line.stamped())
                        .multiply(BigDecimal.valueOf(line.quantity()));
                if (worstBy == null || by.compareTo(worstBy) > 0) {
                    worst = line;
                    worstBy = by;
                }
            }
            return worst;
        }
    }

    /**
     * Collects the lines that moved, separates out the ones that rose, and sums both sides.
     *
     * <p>The stamped total is computed from the LINES rather than read off {@code cart.totalPrice}, so
     * a stored total that has drifted from its own rows cannot turn into a refusal about a price
     * nobody changed.
     */
    static Outcome decide(List<Line> lines) {
        BigDecimal stampedTotal = BigDecimal.ZERO;
        BigDecimal liveTotal = BigDecimal.ZERO;
        List<Line> moved = new ArrayList<>();
        List<Line> risen = new ArrayList<>();

        for (Line line : lines) {
            if (line.stamped() == null) continue;
            BigDecimal quantity = BigDecimal.valueOf(line.quantity());
            BigDecimal live = line.live() != null ? line.live() : line.stamped();

            stampedTotal = stampedTotal.add(line.stamped().multiply(quantity));
            liveTotal = liveTotal.add(live.multiply(quantity));

            // Same comparisons the cart uses, so the two cannot disagree about what "moved" means:
            // by value, not by scale, and never when the live side is unknown.
            boolean unitMoved = CartLinePricing.moved(line.stamped(), line.live());
            // EITHER figure moving needs a re-stamp, not just the charged one. A line stamped at
            // unitPrice=800 / originalUnitPrice=1000 whose listing drops to storePrice=850 with the
            // same 800 sale price still charges 800 — so the old gate skipped it and the basket went
            // on claiming "was 1000" while the product page said "was 850". V61 then copies that
            // figure onto order_items.original_unit_price, whose whole purpose is to record which
            // advertised price was honoured; a number that was never the list price is worse there
            // than no number at all. A line with no live listing is still skipped entirely: null
            // there means "unknown", which is an availability problem, not a price change.
            boolean wasMoved = line.live() != null
                    && CartLinePricing.originalMoved(line.stampedOriginal(), line.liveOriginal());
            if (unitMoved || wasMoved) {
                moved.add(line);
                // Only the CHARGED price can refuse a checkout. A "was" figure that moved on its own
                // costs the customer nothing — it is a display correction, and a 409 over one would
                // stop a checkout to fix a strike-through.
                if (unitMoved && line.live().compareTo(line.stamped()) > 0) {
                    risen.add(line);
                }
            }
        }
        return new Outcome(stampedTotal, liveTotal, List.copyOf(moved), List.copyOf(risen));
    }
}
