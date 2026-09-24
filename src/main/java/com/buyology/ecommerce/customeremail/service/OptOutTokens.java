package com.buyology.ecommerce.customeremail.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The per-customer token behind the unsubscribe link in every marketing email.
 *
 * <p>V50 added the column and backfilled it for everyone who existed at the time, but nothing
 * gives a NEW customer one — there is no default on the column and no code path that sets it at
 * registration. So the column is null for everyone who has registered since, and a sender that
 * reads it raw writes {@code ?token=null} into the footer. The recipient clicks Unsubscribe, is
 * told the link is not valid, and reaches for "mark as spam" instead — which is the one outcome a
 * shared sending domain cannot afford.
 *
 * <p>Minting on demand rather than backfilling again: a backfill fixes today's rows and leaves the
 * same hole open for tomorrow's customers. This closes it for every sender, now and later.
 */
@Component
public class OptOutTokens {

    private static final Logger log = LoggerFactory.getLogger(OptOutTokens.class);

    private final JdbcTemplate jdbc;

    public OptOutTokens(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * This customer's opt-out token, minting one if they have none.
     *
     * <p>Returns null when it cannot produce one, and a caller that gets null must NOT send: an
     * email with a broken unsubscribe link is worse than one not sent.
     */
    public String forUser(UUID userId) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT email_opt_out_token FROM \"users\" WHERE id = ?", userId);
            if (!rows.isEmpty() && rows.get(0).get("email_opt_out_token") != null) {
                return rows.get(0).get("email_opt_out_token").toString();
            }
            UUID fresh = UUID.randomUUID();
            // Conditional so two senders racing on the same customer cannot overwrite each other's
            // token and invalidate a link already sitting in an inbox.
            jdbc.update("UPDATE \"users\" SET email_opt_out_token = ? WHERE id = ? "
                    + "AND email_opt_out_token IS NULL", fresh, userId);
            List<Map<String, Object>> after = jdbc.queryForList(
                    "SELECT email_opt_out_token FROM \"users\" WHERE id = ?", userId);
            Object token = after.isEmpty() ? null : after.get(0).get("email_opt_out_token");
            return token == null ? null : token.toString();
        } catch (Exception e) {
            log.warn("[OPT-OUT] Could not resolve opt-out token for {}: {}", userId, e.getMessage());
            return null;
        }
    }
}
