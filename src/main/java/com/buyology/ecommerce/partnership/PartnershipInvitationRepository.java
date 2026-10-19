package com.buyology.ecommerce.partnership;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.*;

@Repository
public class PartnershipInvitationRepository {
    private final JdbcTemplate jdbc;
    public PartnershipInvitationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record Batch(UUID id, UUID createdBy, Instant createdAt, String templateVersion) {}
    public record Delivery(String email, String status, Instant finishedAt) {}
    public record Claimed(UUID batchId, String email, String subject, String html) {}

    public int insert(UUID id, UUID admin, PartnershipInvitationTemplate.Preview template) {
        return jdbc.update("INSERT INTO partnership_invitation_batches (id, created_by, template_version, subject, html_body) VALUES (?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING",
                id, admin, template.version(), template.subject(), template.html());
    }
    public void addRecipients(UUID id, List<String> emails) {
        jdbc.batchUpdate("INSERT INTO partnership_invitations (batch_id, email) VALUES (?, ?)",
                emails, emails.size(), (ps, email) -> { ps.setObject(1, id); ps.setString(2, email); });
    }
    public Optional<Batch> find(UUID id) {
        return jdbc.query("SELECT id, created_by, created_at, template_version FROM partnership_invitation_batches WHERE id = ?",
                (rs, row) -> new Batch(rs.getObject("id", UUID.class), rs.getObject("created_by", UUID.class),
                        rs.getTimestamp("created_at").toInstant(), rs.getString("template_version")), id).stream().findFirst();
    }
    public List<Delivery> deliveries(UUID id) {
        return jdbc.query("SELECT email, status, finished_at FROM partnership_invitations WHERE batch_id = ? ORDER BY email",
                (rs, row) -> new Delivery(rs.getString("email"), rs.getString("status"),
                        rs.getTimestamp("finished_at") == null ? null : rs.getTimestamp("finished_at").toInstant()), id);
    }
    public List<UUID> recent() {
        return jdbc.query("SELECT id FROM partnership_invitation_batches ORDER BY created_at DESC, id DESC LIMIT 20",
                (rs, row) -> rs.getObject("id", UUID.class));
    }
    /** The short claim transaction commits before any network call; replicas cannot send the same row. */
    public Optional<Claimed> claimNext() {
        return jdbc.query("""
            WITH next AS (
                SELECT i.batch_id, i.email FROM partnership_invitations i
                JOIN partnership_invitation_batches b ON b.id = i.batch_id
                WHERE i.status = 'QUEUED' ORDER BY b.created_at, i.email
                LIMIT 1 FOR UPDATE OF i SKIP LOCKED
            ), claimed AS (
                UPDATE partnership_invitations i SET status = 'SENDING', attempted_at = CURRENT_TIMESTAMP
                FROM next WHERE i.batch_id = next.batch_id AND i.email = next.email
                RETURNING i.batch_id, i.email
            ) SELECT c.batch_id, c.email, b.subject, b.html_body
              FROM claimed c JOIN partnership_invitation_batches b ON b.id = c.batch_id
            """, (rs, row) -> new Claimed(rs.getObject("batch_id", UUID.class), rs.getString("email"),
                rs.getString("subject"), rs.getString("html_body"))).stream().findFirst();
    }
    public void finish(Claimed claimed, String status) {
        if (!Set.of("ACCEPTED", "FAILED", "UNKNOWN").contains(status)) throw new IllegalArgumentException("Invalid delivery outcome");
        jdbc.update("UPDATE partnership_invitations SET status = ?, finished_at = CURRENT_TIMESTAMP WHERE batch_id = ? AND email = ? AND status = 'SENDING'",
                status, claimed.batchId(), claimed.email());
    }
    public void expireInterrupted() {
        jdbc.update("UPDATE partnership_invitations SET status = 'UNKNOWN', finished_at = CURRENT_TIMESTAMP WHERE status = 'SENDING' AND attempted_at < CURRENT_TIMESTAMP - INTERVAL '10 minutes'");
    }
}
