package com.buyology.ecommerce.payout.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Destination details a supplier provides for receiving manual payouts.
 * One row per supplier. Captured via the supplier portal; admins read it
 * from the dashboard when executing the transfer.
 */
@Entity
@Table(name = "supplier_payout_accounts", indexes = {
        @Index(name = "idx_supplier_payout_accounts_supplier", columnList = "supplier_id", unique = true)
})
public class SupplierPayoutAccount {

    @Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "supplier_id", nullable = false, unique = true)
    private UUID supplierId;

    @Column(name = "legal_name", nullable = false, length = 255)
    private String legalName;

    @Column(name = "bank_name", length = 255)
    private String bankName;

    @Column(name = "account_holder_name", length = 255)
    private String accountHolderName;

    @Column(name = "iban", length = 64)
    private String iban;

    @Column(name = "swift_code", length = 32)
    private String swiftCode;

    @Column(name = "wallet_provider", length = 64)
    private String walletProvider;

    @Column(name = "wallet_number", length = 64)
    private String walletNumber;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (this.submittedAt == null) this.submittedAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getSupplierId() { return supplierId; }
    public void setSupplierId(UUID supplierId) { this.supplierId = supplierId; }

    public String getLegalName() { return legalName; }
    public void setLegalName(String legalName) { this.legalName = legalName; }

    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }

    public String getAccountHolderName() { return accountHolderName; }
    public void setAccountHolderName(String accountHolderName) { this.accountHolderName = accountHolderName; }

    public String getIban() { return iban; }
    public void setIban(String iban) { this.iban = iban; }

    public String getSwiftCode() { return swiftCode; }
    public void setSwiftCode(String swiftCode) { this.swiftCode = swiftCode; }

    public String getWalletProvider() { return walletProvider; }
    public void setWalletProvider(String walletProvider) { this.walletProvider = walletProvider; }

    public String getWalletNumber() { return walletNumber; }
    public void setWalletNumber(String walletNumber) { this.walletNumber = walletNumber; }

    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }

    public Instant getUpdatedAt() { return updatedAt; }
}
