package com.buyology.ecommerce.b2b.quote.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A B2B Request-for-Quote. In {@link B2bQuoteStatus#DRAFT} it doubles as the member's
 * B2B cart — there is at most one DRAFT per credential (enforced by the partial unique
 * index {@code uq_b2b_quotes_draft_per_cred}). Owner is keyed on {@code credentialId}
 * (auth_credentials.id / sub) mirroring the V17 one-cart-per-credential rule.
 *
 * Column names mirror V18 (b2b_quotes) exactly so Hibernate ddl-auto=update stays a no-op.
 */
@Entity
@Table(name = "b2b_quotes", indexes = {
        @Index(name = "idx_b2b_quotes_credential", columnList = "credential_id"),
        @Index(name = "idx_b2b_quotes_status", columnList = "status")
})
public class B2bQuote {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Owner — auth_credentials.id (sub). */
    @Column(name = "credential_id", nullable = false)
    private UUID credentialId;

    /** users.id (uid) — resolved from the credential at creation time. */
    @Column(name = "user_id")
    private UUID userId;

    /** b2b_memberships.id of the ACTIVE membership that owns this quote. */
    @Column(name = "membership_id")
    private UUID membershipId;

    /** alpha-3 country code the quote is scoped to. */
    @Column(name = "country_code", nullable = false, length = 3)
    private String countryCode;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private B2bQuoteStatus status = B2bQuoteStatus.DRAFT;

    @Column(name = "member_note", columnDefinition = "text")
    private String memberNote;

    @Column(name = "procurement_note", columnDefinition = "text")
    private String procurementNote;

    /** Payment terms set by procurement when pricing (e.g. "50% advance, 50% on delivery"). */
    @Column(name = "payment_terms", columnDefinition = "text")
    private String paymentTerms;

    /** Terms & conditions set by procurement when pricing. */
    @Column(name = "terms_and_conditions", columnDefinition = "text")
    private String termsAndConditions;

    /** How the member chose to pay this quote — currently only "BANK_TRANSFER" is tracked here. */
    @Column(name = "payment_method", length = 20)
    private String paymentMethod;

    /** Contabo object key of the uploaded bank-transfer proof of payment. */
    @Column(name = "proof_of_payment_key", length = 500)
    private String proofOfPaymentKey;

    @Column(name = "proof_uploaded_at")
    private Instant proofUploadedAt;

    /** When procurement validated the bank-transfer proof (order placed). */
    @Column(name = "payment_verified_at")
    private Instant paymentVerifiedAt;

    /** auth_credentials.id / admin principal that validated the proof. */
    @Column(name = "payment_verified_by")
    private UUID paymentVerifiedBy;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "quoted_at")
    private Instant quotedAt;

    /** auth_credentials.id / admin principal that priced the quote. */
    @Column(name = "quoted_by")
    private UUID quotedBy;

    @Column(name = "valid_until")
    private Instant validUntil;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    /** Set once the accepted quote is checked out into an order. */
    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    private List<B2bQuoteItem> items = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) this.status = B2bQuoteStatus.DRAFT;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getCredentialId() { return credentialId; }
    public void setCredentialId(UUID credentialId) { this.credentialId = credentialId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public UUID getMembershipId() { return membershipId; }
    public void setMembershipId(UUID membershipId) { this.membershipId = membershipId; }

    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public B2bQuoteStatus getStatus() { return status; }
    public void setStatus(B2bQuoteStatus status) { this.status = status; }

    public String getMemberNote() { return memberNote; }
    public void setMemberNote(String memberNote) { this.memberNote = memberNote; }

    public String getProcurementNote() { return procurementNote; }
    public void setProcurementNote(String procurementNote) { this.procurementNote = procurementNote; }

    public String getPaymentTerms() { return paymentTerms; }
    public void setPaymentTerms(String paymentTerms) { this.paymentTerms = paymentTerms; }

    public String getTermsAndConditions() { return termsAndConditions; }
    public void setTermsAndConditions(String termsAndConditions) { this.termsAndConditions = termsAndConditions; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getProofOfPaymentKey() { return proofOfPaymentKey; }
    public void setProofOfPaymentKey(String proofOfPaymentKey) { this.proofOfPaymentKey = proofOfPaymentKey; }

    public Instant getProofUploadedAt() { return proofUploadedAt; }
    public void setProofUploadedAt(Instant proofUploadedAt) { this.proofUploadedAt = proofUploadedAt; }

    public Instant getPaymentVerifiedAt() { return paymentVerifiedAt; }
    public void setPaymentVerifiedAt(Instant paymentVerifiedAt) { this.paymentVerifiedAt = paymentVerifiedAt; }

    public UUID getPaymentVerifiedBy() { return paymentVerifiedBy; }
    public void setPaymentVerifiedBy(UUID paymentVerifiedBy) { this.paymentVerifiedBy = paymentVerifiedBy; }

    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }

    public Instant getQuotedAt() { return quotedAt; }
    public void setQuotedAt(Instant quotedAt) { this.quotedAt = quotedAt; }

    public UUID getQuotedBy() { return quotedBy; }
    public void setQuotedBy(UUID quotedBy) { this.quotedBy = quotedBy; }

    public Instant getValidUntil() { return validUntil; }
    public void setValidUntil(Instant validUntil) { this.validUntil = validUntil; }

    public Instant getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(Instant acceptedAt) { this.acceptedAt = acceptedAt; }

    public UUID getOrderId() { return orderId; }
    public void setOrderId(UUID orderId) { this.orderId = orderId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public List<B2bQuoteItem> getItems() { return items; }
    public void setItems(List<B2bQuoteItem> items) { this.items = items; }
}
