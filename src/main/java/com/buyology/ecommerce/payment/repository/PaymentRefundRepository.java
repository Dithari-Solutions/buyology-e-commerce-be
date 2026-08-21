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

    /**
     * Refunded-or-possibly-refunded total for a transaction: SUCCESS plus PENDING.
     *
     * <p>The guard that stops a double refund has to answer "how much has already left", and a
     * PENDING row is money that may well have left. It is written before the gateway is called and
     * only becomes SUCCESS or FAILED once the gateway answers — so a row still PENDING is one where
     * the answer never arrived: a timeout, a crash, a rolled-back transaction. Counting only
     * SUCCESS treats every one of those as "no money moved" and lets a retry send it again.
     *
     * <p>Being wrong in this direction is recoverable: a refund blocked by a stale PENDING row is a
     * support ticket. Being wrong in the other direction pays a customer twice and nothing detects
     * it.
     */
    @Query("SELECT COALESCE(SUM(r.amount), 0) FROM PaymentRefund r " +
           "WHERE r.transaction = :transaction AND r.status IN " +
           "(com.buyology.ecommerce.payment.enums.RefundStatus.SUCCESS, " +
           " com.buyology.ecommerce.payment.enums.RefundStatus.PENDING)")
    BigDecimal sumRefundedOrInFlight(@Param("transaction") PaymentTransaction transaction);
}
