package com.buyology.ecommerce.promo.repository;

import com.buyology.ecommerce.promo.domain.PromoCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface PromoCodeRepository extends JpaRepository<PromoCode, UUID> {
    Optional<PromoCode> findByCodeIgnoreCaseAndIsActiveTrue(String code);
    Optional<PromoCode> findByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCase(String code);

    /**
     * The promo row, locked for writing.
     *
     * <p>Reserving a code is a read-check-insert against its own usage count, and two checkouts
     * racing for the last use of a code would otherwise both read "one left" and both take it. The
     * lock makes those three steps one, and is held until the reserving transaction commits.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PromoCode p WHERE p.id = :id")
    Optional<PromoCode> findByIdForUpdate(@Param("id") UUID id);

    /** How many token-redemption codes this customer has minted (for the per-customer redeem cap). */
    long countByTargetUserIdAndRedeemedFromTokensNotNull(UUID targetUserId);
}
