package com.buyology.ecommerce.customeremail.service;

/**
 * Who may be sent marketing email, written once.
 *
 * <p>This used to live as a private constant inside {@link CustomerEmailService}, whose javadoc
 * already said the preview and the send must share it so they can never disagree. The same
 * reasoning now extends past that one class: an abandoned-cart reminder is marketing mail too, and
 * a second hand-written copy of these rules is how someone who used the unsubscribe link ends up
 * receiving mail from the one path that forgot a clause.
 *
 * <p>Three of the four bulk senders in this codebase do not consult these rules at all — the promo
 * broadcast, the newsletter and the streak reminder. That is a known debt, not a precedent to
 * follow.
 */
public final class MarketingAudience {

    private MarketingAudience() {}

    /**
     * The suppression predicate, for a query that has {@code users u}, {@code auth_credentials c}
     * and a LEFT JOIN of {@code newsletter_subscribers ns} in scope.
     *
     * <p>Every clause is a person who must not receive marketing mail: a suspended or deleted
     * account, a guest checkout that was never a registered customer, someone who used our own
     * opt-out link, and someone who unsubscribed from the newsletter with the same address. The
     * last one is the join nothing else in the codebase makes.
     */
    public static final String ELIGIBLE_WHERE = """
            u.user_type = 'CUSTOMER'
              AND u.status = 'ACTIVE'
              AND u.deleted_at IS NULL
              AND COALESCE(u.is_guest, FALSE) = FALSE
              AND u.email_opt_out_at IS NULL
              AND c.email IS NOT NULL AND c.email <> ''
              AND (ns.id IS NULL OR ns.is_active = TRUE)
            """;

    /** The tables {@link #ELIGIBLE_WHERE} expects, for a query that starts from the customer. */
    public static final String ELIGIBLE_FROM = """
            FROM "users" u
            JOIN "auth_credentials" c ON c.user_id = u.id
            LEFT JOIN newsletter_subscribers ns ON LOWER(ns.email) = LOWER(c.email)
            """;

    /** Every mailable customer: one row per credential, as {@code user_id} and {@code email}. */
    public static final String ELIGIBLE =
            "SELECT u.id AS user_id, LOWER(c.email) AS email\n" + ELIGIBLE_FROM + "WHERE " + ELIGIBLE_WHERE;
}
