package com.buyology.ecommerce.b2b.quote.repository;

import com.buyology.ecommerce.b2b.quote.domain.B2bQuote;
import com.buyology.ecommerce.b2b.quote.domain.B2bQuoteStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface B2bQuoteRepository extends JpaRepository<B2bQuote, UUID> {

    /** The active B2B cart — one DRAFT per credential (partial unique index). */
    Optional<B2bQuote> findByCredentialIdAndStatus(UUID credentialId, B2bQuoteStatus status);

    /** A member's quote history, newest first. */
    List<B2bQuote> findByCredentialIdOrderByCreatedAtDesc(UUID credentialId);

    /** Procurement queue — filtered by status, newest first. */
    List<B2bQuote> findByStatusOrderByCreatedAtDesc(B2bQuoteStatus status);

    /** Sidebar badge — how many quotes are awaiting a price (SUBMITTED). */
    long countByStatus(B2bQuoteStatus status);
}
