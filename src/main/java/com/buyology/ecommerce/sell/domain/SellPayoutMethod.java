package com.buyology.ecommerce.sell.domain;

/**
 * How the customer takes the money once they accept a buy-back offer.
 *
 * <ul>
 *   <li>{@link #STORE_CASH} — collect the payout at the store branch. The only method available
 *       today; the store hands over the money and procurement marks the request COMPLETED.</li>
 *   <li>{@link #WALLET_CREDIT} — keep the amount as Buyology wallet credit to spend later.
 *       <strong>Not available yet</strong>: there is no customer wallet ledger to credit, so the
 *       service rejects this choice and the storefront shows it as "coming soon". The constant
 *       exists so the column, the API and the UI don't need reshaping when the wallet lands.</li>
 * </ul>
 */
public enum SellPayoutMethod {
    STORE_CASH,
    WALLET_CREDIT
}
