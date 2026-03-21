package com.buyology.ecommerce.payment.repository;

import com.buyology.ecommerce.payment.domain.PaymentRefund;
import com.buyology.ecommerce.payment.domain.PaymentTransaction;
import com.buyology.ecommerce.payment.enums.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, UUID> {

    List<PaymentRefund> findAllByTransaction(PaymentTransaction transaction);

    // Used for partial refund guard — sum of all successful refunds for a transaction
    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM PaymentRefund r " +
           "WHERE r.transaction = :transaction AND r.status = :status")
    BigDecimal sumAmountByTransactionAndStatus(
            @Param("transaction") PaymentTransaction transaction,
            @Param("status") RefundStatus status);
}
