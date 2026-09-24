package com.buyology.ecommerce.cart.reminder;

import com.buyology.ecommerce.customeremail.service.MarketingAudience;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Finds the carts worth writing to, and records that we did.
 *
 * <p>Raw SQL rather than JPA for two reasons. The eligibility rules live in SQL already
 * ({@link MarketingAudience}) and must not be restated in another dialect, and the stamp at the end
 * must NOT go through the entity: {@code Cart} bumps {@code updated_at} on every save, which would
 * make a reminded cart look freshly touched and re-arm its own reminder on the next sweep.
 */
@Repository
public class CartReminderRepository {

    /**
     * A cart that has gone quiet with something in it, belonging to someone we may write to.
     *
     * <p>Each clause is a way the naive version gets it wrong:
     * <ul>
     *   <li><b>status = 'ACTIVE'</b> — {@code ABANDONED} in this schema means the opposite of what
     *       it sounds like: it is stamped on a cart that was successfully bought, and on Buy Now's
     *       spent throwaway carts. Reminding on that status would mail people who already paid.</li>
     *   <li><b>selected lines only</b> — an unticked line is a deliberate "not this time", kept on
     *       purpose across checkout. Nagging someone about a parked item is not a reminder.</li>
     *   <li><b>no order since the cart went quiet</b> — from this cart or any other. Note the
     *       "since": a blanket "this cart never produced an order" excludes the two groups most
     *       worth writing to, and excludes them forever. Pressing Pay and closing the tab writes a
     *       PENDING_PAYMENT order against the cart, so the shopper who got furthest would be the
     *       one we never remind; and a partially converted cart keeps its id, so a customer who
     *       once bought two of three items would be silently skipped for the life of that row.</li>
     *   <li><b>reminder_sent_at</b> — null, or older than the cart's own last change. That is the
     *       re-arm rule: adding something new after a reminder makes the cart eligible again,
     *       while an untouched cart is written to exactly once.</li>
     *   <li><b>an upper bound on age</b> — without it the first run in production would be a
     *       broadcast. This feature has never run, the carts table is never pruned, and so every
     *       cart ever abandoned has a null reminder stamp: the sweep would mail people about a
     *       basket they filled a year ago, from a domain with no bounce handling. A cart that has
     *       been cold for longer than the window is not forgotten, it is over.</li>
     *
     *   <li><b>a market that can actually check out</b> — most regions are browse-only
     *       ({@code paymentsEnabled: false} in the storefront's market list), and this email says
     *       "Complete my order", quotes Tabby and Tamara and promises free delivery over AED 100.
     *       None of that is true outside the UAE, so nobody outside it is written to. It also
     *       keeps a legacy cart with a null currency from having its total printed as AED.</li>
     * </ul>
     *
     * <p>One row per inbox. A person can hold two credentials with the same address — email and
     * Google — and so two carts; without the DISTINCT they would get two reminders minutes apart,
     * and unsubscribing from one would not stop the other. The freshest cart wins. The dedupe sits
     * in a subquery because DISTINCT ON must be ordered by its own key, which would otherwise
     * replace the oldest-first order the batch limit depends on.
     *
     * <p>Oldest first and capped by the caller, so a backlog drains over several runs in a
     * predictable order rather than in one burst.
     */
    private static final String CANDIDATES = """
            SELECT s.cart_id, s.currency, s.user_id, s.first_name, s.email
            FROM (
                SELECT DISTINCT ON (LOWER(c.email))
                       cart.id            AS cart_id,
                       cart.currency      AS currency,
                       cart.updated_at    AS updated_at,
                       u.id               AS user_id,
                       u.first_name       AS first_name,
                       LOWER(c.email)     AS email
                FROM carts cart
                JOIN "auth_credentials" c ON c.id = cart.auth_credential_id
                JOIN "users" u ON u.id = c.user_id
                LEFT JOIN newsletter_subscribers ns ON LOWER(ns.email) = LOWER(c.email)
                WHERE %s
                  AND cart.status = 'ACTIVE'
                  AND cart.updated_at < ?
                  AND cart.updated_at > ?
                  AND cart.currency = 'AED'
                  AND (cart.reminder_sent_at IS NULL OR cart.reminder_sent_at < cart.updated_at)
                  AND EXISTS (SELECT 1 FROM cart_items ci
                              WHERE ci.cart_id = cart.id AND ci.selected = TRUE)
                  AND NOT EXISTS (SELECT 1 FROM orders o
                                  WHERE o.user_id = u.id AND o.created_at > cart.updated_at)
                ORDER BY LOWER(c.email), cart.updated_at DESC
            ) s
            ORDER BY s.updated_at
            LIMIT ?
            """.formatted(MarketingAudience.ELIGIBLE_WHERE);

    /**
     * The lines to show, in the language the templates are written in.
     *
     * <p>Titles live per language in {@code product_translations} — there is no title on the
     * product itself — and every email template in this system is English, so English is what the
     * email asks for. {@code COALESCE} keeps a product whose English translation is missing: a
     * line with a blank name still tells the shopper their cart is not empty, whereas dropping it
     * would quietly under-report the cart and misstate its total.
     */
    private static final String LINES = """
            SELECT ci.quantity                                   AS quantity,
                   ci.total_price                                AS total_price,
                   COALESCE(en.title, any_t.title, p.sku, '')    AS title
            FROM cart_items ci
            JOIN products p ON p.id = ci.product_id
            LEFT JOIN product_translations en
                   ON en.product_id = p.id AND UPPER(en.language) = 'EN'
            LEFT JOIN LATERAL (
                   SELECT t.title FROM product_translations t
                   WHERE t.product_id = p.id ORDER BY t.language LIMIT 1
            ) any_t ON TRUE
            WHERE ci.cart_id = ? AND ci.selected = TRUE
            ORDER BY ci.created_at
            """;

    private final JdbcTemplate jdbc;

    public CartReminderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Carts last touched between {@code notOlderThan} and {@code quietSince}, at most
     * {@code limit} of them — quiet long enough to have been abandoned, recent enough to still
     * mean something to the shopper.
     */
    public List<Candidate> findCandidates(Instant quietSince, Instant notOlderThan, int limit) {
        return jdbc.query(CANDIDATES,
                (rs, i) -> new Candidate(
                        rs.getObject("cart_id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getString("email"),
                        rs.getString("first_name"),
                        rs.getString("currency")),
                Timestamp.from(quietSince), Timestamp.from(notOlderThan), limit);
    }

    /** The selected lines of one cart. */
    public List<Line> findLines(UUID cartId) {
        return jdbc.query(LINES,
                (rs, i) -> new Line(
                        rs.getString("title"),
                        rs.getInt("quantity"),
                        rs.getBigDecimal("total_price")),
                cartId);
    }

    /**
     * Records that this cart has now been written about.
     *
     * <p>A direct UPDATE of one column. Going through the entity would fire {@code @PreUpdate} and
     * move {@code updated_at} with it, which re-arms the very reminder this is closing.
     */
    public void markReminded(UUID cartId, Instant at) {
        jdbc.update("UPDATE carts SET reminder_sent_at = ? WHERE id = ?", Timestamp.from(at), cartId);
    }

    /** One cart worth writing about, and the person to write to. */
    public record Candidate(UUID cartId, UUID userId, String email, String firstName,
                            String currency) {}

    /** One selected line of a cart, as the email prints it. */
    public record Line(String title, int quantity, java.math.BigDecimal totalPrice) {}
}
