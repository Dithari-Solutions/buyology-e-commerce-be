package com.buyology.ecommerce.customeremail.service;

import com.buyology.ecommerce.common.service.EmailService;
import com.buyology.ecommerce.customeremail.domain.CustomerEmailCampaign;
import com.buyology.ecommerce.customeremail.domain.CustomerEmailRecipient;
import com.buyology.ecommerce.customeremail.repository.CustomerEmailCampaignRepository;
import com.buyology.ecommerce.customeremail.repository.CustomerEmailRecipientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sending an email to customers, with the parts that make it safe to hand to a marketer.
 *
 * <p>Three things this does that the broadcasts already in the codebase do not.
 *
 * <p><b>It suppresses people who asked not to be mailed.</b> Until this table existed the only
 * unsubscribe signal in the system was {@code newsletter_subscribers.is_active}, and nothing joined
 * it to a customer — so "email all customers" would have mailed everyone who had ever clicked
 * unsubscribe. That is the mistake you cannot take back, and on a shared-reputation provider it is
 * also the one that gets the sending domain suspended.
 *
 * <p><b>It records an outcome per recipient.</b> The existing broadcasts log the number of rows
 * they iterated over as the number of emails sent, so a run in which every message failed reports
 * the same number as one in which every message arrived. Here the boolean the provider gives back
 * is what gets written.
 *
 * <p><b>It cannot start twice.</b> The DRAFT to SENDING move is a conditional UPDATE, so a
 * double-clicked button, a retried request and the second application host all race for one row and
 * exactly one wins. A disabled button is not a guard.
 */
@Service
public class CustomerEmailService {

    private static final Logger log = LoggerFactory.getLogger(CustomerEmailService.class);

    /**
     * Who may be mailed, expressed once so the preview and the send can never disagree.
     *
     * <p>Every clause is a person who must not receive marketing mail: a suspended or deleted
     * account, a guest checkout that was never a registered customer, someone who used our own
     * opt-out link, and someone who unsubscribed from the newsletter with the same address. The
     * last one is the join nothing else in the codebase makes.
     *
     * <p>Ordered by id so paging is deterministic — the existing broadcast pages an unordered
     * query, which can skip and repeat rows across pages.
     */
    private static final String ELIGIBLE = """
            SELECT u.id AS user_id, LOWER(c.email) AS email
            FROM "users" u
            JOIN "auth_credentials" c ON c.user_id = u.id
            LEFT JOIN newsletter_subscribers ns ON LOWER(ns.email) = LOWER(c.email)
            WHERE u.user_type = 'CUSTOMER'
              AND u.status = 'ACTIVE'
              AND u.deleted_at IS NULL
              AND COALESCE(u.is_guest, FALSE) = FALSE
              AND u.email_opt_out_at IS NULL
              AND c.email IS NOT NULL AND c.email <> ''
              AND (ns.id IS NULL OR ns.is_active = TRUE)
            """;

    private final JdbcTemplate jdbc;
    private final CustomerEmailCampaignRepository campaignRepo;
    private final CustomerEmailRecipientRepository recipientRepo;
    private final EmailService emailService;
    private final CustomerEmailGuard guard;
    private final String baseUrl;

    public CustomerEmailService(JdbcTemplate jdbc,
                                CustomerEmailCampaignRepository campaignRepo,
                                CustomerEmailRecipientRepository recipientRepo,
                                EmailService emailService,
                                CustomerEmailGuard guard,
                                @Value("${app.base-url:https://buyology.online}") String baseUrl) {
        this.jdbc = jdbc;
        this.campaignRepo = campaignRepo;
        this.recipientRepo = recipientRepo;
        this.emailService = emailService;
        this.guard = guard;
        this.baseUrl = baseUrl;
    }

    // ── Preview ───────────────────────────────────────────────────────────────

    /**
     * How many people a given audience actually resolves to, after suppression.
     *
     * <p>This number is what the admin is shown and what they must echo back to start the send, so
     * it is deliberately computed by the same query the send uses. "Select all" being a server-side
     * audience rather than a list of ids assembled in the browser matters here: the browser only
     * ever holds one page.
     */
    public Map<String, Object> preview(CustomerEmailCampaign.Audience audience, List<UUID> userIds) {
        List<Map<String, Object>> rows = resolve(audience, userIds);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("recipientCount", rows.size());
        out.put("audience", audience.name());
        out.put("sample", rows.stream().limit(5).map(r -> r.get("email")).toList());
        return out;
    }

    private List<Map<String, Object>> resolve(CustomerEmailCampaign.Audience audience, List<UUID> userIds) {
        if (audience == CustomerEmailCampaign.Audience.ALL_CUSTOMERS) {
            return jdbc.queryForList(ELIGIBLE + " ORDER BY u.id");
        }
        if (userIds == null || userIds.isEmpty()) return List.of();
        // Same eligibility rules for a hand-picked list: choosing someone explicitly does not make
        // it acceptable to mail them after they opted out.
        String sql = ELIGIBLE + " AND u.id = ANY (?::uuid[]) ORDER BY u.id";
        String array = "{" + String.join(",", userIds.stream().map(UUID::toString).toList()) + "}";
        return jdbc.queryForList(sql, array);
    }

    // ── Create ────────────────────────────────────────────────────────────────

    /**
     * Stores the campaign and freezes its recipient list.
     *
     * @param confirmRecipientCount the count the admin was shown. A mismatch is refused: it is the
     *                              number, not the prose, that a human notices when they meant to
     *                              select five people and the audience says six hundred.
     */
    @Transactional
    public CustomerEmailCampaign create(String subject, String bodyHtml,
                                        CustomerEmailCampaign.Audience audience, List<UUID> userIds,
                                        int confirmRecipientCount, UUID adminId, String adminName) {
        if (subject == null || subject.isBlank()) throw new IllegalArgumentException("A subject is required.");
        if (bodyHtml == null || bodyHtml.isBlank()) throw new IllegalArgumentException("The email body is empty.");
        if (audience == CustomerEmailCampaign.Audience.SELECTED && (userIds == null || userIds.isEmpty())) {
            throw new IllegalArgumentException("Select at least one customer, or choose all customers.");
        }
        if (audience == CustomerEmailCampaign.Audience.ALL_CUSTOMERS && userIds != null && !userIds.isEmpty()) {
            // A request carrying both is ambiguous, and the ambiguous reading is the expensive one.
            throw new IllegalArgumentException("Choose either specific customers or all customers, not both.");
        }

        List<Map<String, Object>> rows = resolve(audience, userIds);
        if (rows.isEmpty()) throw new IllegalArgumentException("That audience resolves to nobody.");
        if (rows.size() != confirmRecipientCount) {
            throw new IllegalStateException("This would reach " + rows.size() + " customers, not "
                    + confirmRecipientCount + ". Review the audience and try again.");
        }
        guard.checkCampaign(adminId, rows.size());

        CustomerEmailCampaign campaign = new CustomerEmailCampaign();
        campaign.setSubject(subject.trim());
        campaign.setBodyHtml(bodyHtml);
        campaign.setAudience(audience);
        campaign.setRecipientCount(rows.size());
        campaign.setCreatedByAdminId(adminId);
        campaign.setCreatedByAdminName(adminName);
        campaignRepo.save(campaign);

        List<CustomerEmailRecipient> recipients = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            recipients.add(new CustomerEmailRecipient(
                    campaign.getId(), (UUID) r.get("user_id"), (String) r.get("email")));
        }
        recipientRepo.saveAll(recipients);
        return campaign;
    }

    // ── Send ──────────────────────────────────────────────────────────────────

    /**
     * Claims the campaign and hands the work to a background thread.
     *
     * <p>The claim is the whole concurrency story: {@code UPDATE ... WHERE status = 'DRAFT'} either
     * affects one row or none, and only the winner dispatches.
     */
    @Transactional
    public boolean startSending(UUID campaignId) {
        int claimed = campaignRepo.claimForSending(campaignId, Instant.now());
        if (claimed != 1) return false;
        // Dispatched through the bean so the @Async proxy applies; a direct call would run the
        // whole loop on the request thread, which is how this application has taken itself down.
        selfRef().runCampaign(campaignId);
        return true;
    }

    private CustomerEmailService self;
    @org.springframework.beans.factory.annotation.Autowired
    public void setSelf(@org.springframework.context.annotation.Lazy CustomerEmailService self) { this.self = self; }
    private CustomerEmailService selfRef() { return self == null ? this : self; }

    /**
     * The send loop, off the request thread.
     *
     * <p>No database transaction spans the provider calls. The recipients are read once, each send
     * happens outside a transaction, and each outcome is written in its own short one — because a
     * connection held across hundreds of network calls is exactly the pattern that starves the pool
     * for every other request on the host.
     */
    @Async
    public void runCampaign(UUID campaignId) {
        CustomerEmailCampaign campaign = campaignRepo.findById(campaignId).orElse(null);
        if (campaign == null) return;

        List<CustomerEmailRecipient> recipients = recipientRepo.findByCampaignIdOrderByEmailAsc(campaignId);
        int sent = 0, failed = 0, consecutiveFailures = 0;
        String abort = null;

        for (CustomerEmailRecipient r : recipients) {
            String token = optOutTokenFor(r.getUserId());
            String unsubscribe = baseUrl + "/api/email/opt-out?token=" + token;
            boolean ok = emailService.sendCustomerCampaignEmail(
                    r.getEmail(), campaign.getSubject(), campaign.getBodyHtml(), unsubscribe);

            if (ok) {
                sent++;
                consecutiveFailures = 0;
                recordOutcome(r.getId(), CustomerEmailRecipient.Status.SENT, null);
            } else {
                failed++;
                consecutiveFailures++;
                recordOutcome(r.getId(), CustomerEmailRecipient.Status.FAILED, "Provider rejected the message");
            }

            // A revoked or blank API key fails every send identically. Without this the run
            // produces one ERROR line per customer and no way to tell a dead provider from a bad
            // address — which is precisely how a real failure got buried here before.
            if (consecutiveFailures >= 10) {
                abort = "Aborted after 10 consecutive failures — the email provider looks unavailable.";
                break;
            }
        }
        finish(campaignId, sent, failed, abort);
        log.info("[CUSTOMER-EMAIL] Campaign {} finished: {} sent, {} failed{}",
                campaignId, sent, failed, abort == null ? "" : " (" + abort + ")");
    }

    private String optOutTokenFor(UUID userId) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT email_opt_out_token FROM \"users\" WHERE id = ?", userId);
            if (!rows.isEmpty() && rows.get(0).get("email_opt_out_token") != null) {
                return rows.get(0).get("email_opt_out_token").toString();
            }
            UUID fresh = UUID.randomUUID();
            jdbc.update("UPDATE \"users\" SET email_opt_out_token = ? WHERE id = ? "
                    + "AND email_opt_out_token IS NULL", fresh, userId);
            return fresh.toString();
        } catch (Exception e) {
            log.warn("[CUSTOMER-EMAIL] Could not resolve opt-out token for {}: {}", userId, e.getMessage());
            return "";
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordOutcome(UUID recipientId, CustomerEmailRecipient.Status status, String error) {
        recipientRepo.findById(recipientId).ifPresent(r -> {
            r.setStatus(status);
            r.setError(error);
            r.setSentAt(Instant.now());
            recipientRepo.save(r);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finish(UUID campaignId, int sent, int failed, String abortReason) {
        campaignRepo.findById(campaignId).ifPresent(c -> {
            c.setSentCount(sent);
            c.setFailedCount(failed);
            c.setFinishedAt(Instant.now());
            c.setAbortReason(abortReason);
            c.setStatus(abortReason != null ? CustomerEmailCampaign.Status.ABORTED
                    : failed > 0 && sent == 0 ? CustomerEmailCampaign.Status.FAILED
                    : CustomerEmailCampaign.Status.SENT);
            campaignRepo.save(c);
        });
    }

    // ── Opt out ───────────────────────────────────────────────────────────────

    /** Honours the unsubscribe link. Idempotent: clicking twice is not an error. */
    @Transactional
    public boolean optOut(UUID token) {
        int updated = jdbc.update(
                "UPDATE \"users\" SET email_opt_out_at = NOW() "
                        + "WHERE email_opt_out_token = ? AND email_opt_out_at IS NULL", token);
        if (updated == 0) {
            Integer exists = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM \"users\" WHERE email_opt_out_token = ?", Integer.class, token);
            return exists != null && exists > 0;
        }
        return true;
    }
}
