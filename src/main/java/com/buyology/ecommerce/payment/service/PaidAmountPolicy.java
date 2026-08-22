package com.buyology.ecommerce.payment.service;

import com.buyology.ecommerce.order.domain.Order;
import com.buyology.ecommerce.payment.domain.PaymentTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Whether a settled payment covers what an order is owed.
 *
 * <p>Moved verbatim out of OrderService so the classification of an unapplied payment can be
 * tested without its thirty-odd collaborators. The behaviour is deliberately unchanged, including
 * the two judgement calls: the 1% tolerance (FX conversion between the order's currency and the
 * charged currency rounds; a customer who paid what we quoted must never be flagged over a
 * rounding tail) and the fail-open catch (a currency-service outage must not stall every payment
 * in PENDING_PAYMENT — the sweep re-checks later).
 */
@Component
public class PaidAmountPolicy {

    private static final Logger log = LoggerFactory.getLogger(PaidAmountPolicy.class);

    private final com.buyology.ecommerce.currency.service.CurrencyExchangeService currencyExchangeService;

    public PaidAmountPolicy(com.buyology.ecommerce.currency.service.CurrencyExchangeService currencyExchangeService) {
        this.currencyExchangeService = currencyExchangeService;
    }

    public boolean covers(Order order, PaymentTransaction tx) {
        try {
            BigDecimal due = order.getTotalAmount() == null ? BigDecimal.ZERO : order.getTotalAmount();
            if (order.getCreditApplied() != null && order.getCreditApplied().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal creditInOrderCcy = order.getCreditCurrency() != null
                        && order.getCreditCurrency().equalsIgnoreCase(order.getCurrency())
                        ? order.getCreditApplied()
                        : currencyExchangeService.convert(order.getCreditApplied(),
                                order.getCreditCurrency(), order.getCurrency());
                due = due.subtract(creditInOrderCcy).max(BigDecimal.ZERO);
            }
            BigDecimal dueInTxCcy = order.getCurrency() != null
                    && order.getCurrency().equalsIgnoreCase(tx.getCurrency())
                    ? due
                    : currencyExchangeService.convert(due, order.getCurrency(), tx.getCurrency());
            BigDecimal paid = tx.getAmount() == null ? BigDecimal.ZERO : tx.getAmount();
            return paid.compareTo(dueInTxCcy.multiply(new BigDecimal("0.99"))) >= 0;
        } catch (Exception e) {
            log.error("[PAYMENT] Amount reconciliation failed for order {} / tx {}: {} — allowing payment.",
                    order.getId(), tx.getId(), e.getMessage());
            return true;
        }
    }
}
