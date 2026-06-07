package com.buyology.ecommerce.membership.repository;

import com.buyology.ecommerce.membership.domain.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    Optional<Wallet> findByUserId(UUID userId);

    /**
     * Pessimistic-write variant used for all balance mutations (credit / debit /
     * adjustment). Serializes concurrent read-modify-write on the same wallet so two
     * requests can't both pass the balance check and double-spend / overdraw.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.userId = :userId")
    Optional<Wallet> findByUserIdForUpdate(@Param("userId") UUID userId);

    boolean existsByUserId(UUID userId);
}
