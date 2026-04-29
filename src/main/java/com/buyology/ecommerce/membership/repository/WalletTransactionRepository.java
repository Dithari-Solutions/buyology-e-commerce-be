package com.buyology.ecommerce.membership.repository;

import com.buyology.ecommerce.membership.domain.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID> {

    List<WalletTransaction> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<WalletTransaction> findByWalletIdOrderByCreatedAtDesc(UUID walletId);
}
