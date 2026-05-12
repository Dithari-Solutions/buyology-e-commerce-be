package com.buyology.ecommerce.payout.service;

import com.buyology.ecommerce.order.domain.OrderItem;
import com.buyology.ecommerce.order.repository.OrderItemRepository;
import com.buyology.ecommerce.payout.domain.PayoutRequest;
import com.buyology.ecommerce.payout.domain.SupplierPayoutAccount;
import com.buyology.ecommerce.payout.dto.PayoutEligibilityResponse;
import com.buyology.ecommerce.payout.enums.PayoutRequestStatus;
import com.buyology.ecommerce.payout.repository.PayoutRequestRepository;
import com.buyology.ecommerce.payout.repository.SupplierPayoutAccountRepository;
import com.buyology.ecommerce.supplier.domain.Supplier;
import com.buyology.ecommerce.supplier.repository.SupplierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PayoutEligibilityService {

    public static final int PAYOUT_INTERVAL_DAYS = 15;

    private final SupplierRepository supplierRepository;
    private final SupplierPayoutAccountRepository accountRepository;
    private final PayoutRequestRepository requestRepository;
    private final OrderItemRepository orderItemRepository;

    public PayoutEligibilityService(SupplierRepository supplierRepository,
                                    SupplierPayoutAccountRepository accountRepository,
                                    PayoutRequestRepository requestRepository,
                                    OrderItemRepository orderItemRepository) {
        this.supplierRepository = supplierRepository;
        this.accountRepository = accountRepository;
        this.requestRepository = requestRepository;
        this.orderItemRepository = orderItemRepository;
    }

    @Transactional(readOnly = true)
    public PayoutEligibilityResponse compute(UUID supplierId) {
        Supplier supplier = supplierRepository.findById(supplierId).orElse(null);
        if (supplier == null) {
            return new PayoutEligibilityResponse(false, BigDecimal.ZERO, null, "Supplier account not found");
        }

        Optional<SupplierPayoutAccount> account = accountRepository.findBySupplierId(supplierId);
        Optional<PayoutRequest> openPending = requestRepository
                .findFirstBySupplierIdAndStatus(supplierId, PayoutRequestStatus.PENDING);
        Optional<PayoutRequest> lastPaid = requestRepository
                .findFirstBySupplierIdAndStatusOrderByPaidAtDesc(supplierId, PayoutRequestStatus.PAID);

        List<OrderItem> eligibleItems = orderItemRepository.findPayoutEligibleBySupplierId(supplierId);
        BigDecimal owed = eligibleItems.stream()
                .map(OrderItem::getTotalPrice)
                .filter(p -> p != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Instant anchor = lastPaid.map(PayoutRequest::getPaidAt).orElse(supplier.getCreatedAt());
        Instant nextEligibleAt = anchor.plus(PAYOUT_INTERVAL_DAYS, ChronoUnit.DAYS);
        LocalDate nextEligibleDate = nextEligibleAt.atZone(ZoneOffset.UTC).toLocalDate();
        boolean windowReached = !Instant.now().isBefore(nextEligibleAt);

        if (openPending.isPresent()) {
            BigDecimal openAmount = openPending.get().getAmountAed();
            return new PayoutEligibilityResponse(
                    false, owed, null,
                    "Your payout request of AED " + openAmount.toPlainString() + " is pending — we'll process it shortly"
            );
        }
        if (account.isEmpty()) {
            return new PayoutEligibilityResponse(
                    false, owed, windowReached ? null : nextEligibleDate,
                    "Add your payout details to request a payout"
            );
        }
        if (!windowReached) {
            return new PayoutEligibilityResponse(
                    false, owed, nextEligibleDate,
                    "Your next payout will be available on " + nextEligibleDate
            );
        }
        if (owed.signum() <= 0) {
            return new PayoutEligibilityResponse(
                    false, BigDecimal.ZERO, null,
                    "No revenue is currently available for payout"
            );
        }
        return new PayoutEligibilityResponse(
                true, owed, null,
                "You can request your payout today — AED " + owed.toPlainString() + " available"
        );
    }
}
