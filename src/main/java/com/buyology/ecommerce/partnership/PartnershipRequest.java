package com.buyology.ecommerce.partnership;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="partnership_requests")
public class PartnershipRequest {
    @Id private UUID id;
    @Column(nullable=false, columnDefinition="text") private String payload;
    @Column(nullable=false, length=20) private String status = "NEW";
    @Column(name="admin_notes", columnDefinition="text") private String adminNotes;
    @Column(name="created_at", nullable=false) private Instant createdAt;
    @Column(name="email_status", nullable=false, length=20) private String emailStatus = "PENDING";
    @Column(name="email_attempts", nullable=false) private int emailAttempts;
    @Column(name="next_email_attempt", nullable=false) private Instant nextEmailAttempt;
    public UUID getId() { return id; }
    public String getPayload() { return payload; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status=status; }
    public String getAdminNotes() { return adminNotes; }
    public void setAdminNotes(String notes) { this.adminNotes=notes; }
    public Instant getCreatedAt() { return createdAt; }
    public String getEmailStatus() { return emailStatus; }
    public void setEmailStatus(String status) { this.emailStatus=status; }
    public int getEmailAttempts() { return emailAttempts; }
    public void setEmailAttempts(int attempts) { this.emailAttempts=attempts; }
    public void setNextEmailAttempt(Instant next) { this.nextEmailAttempt=next; }
}
