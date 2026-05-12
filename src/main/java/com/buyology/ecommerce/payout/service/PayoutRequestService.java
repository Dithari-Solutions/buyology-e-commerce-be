package com.buyology.ecommerce.payout.service;

import com.buyology.ecommerce.order.domain.OrderItem;
import com.buyology.ecommerce.order.repository.OrderItemRepository;
import com.buyology.ecommerce.payout.domain.PayoutRequest;
import com.buyology.ecommerce.payout.domain.PayoutRequestOrderItem;
import com.buyology.ecommerce.payout.domain.SupplierPayoutAccount;
import com.buyology.ecommerce.payout.dto.PayoutEligibilityResponse;
import com.buyology.ecommerce.payout.dto.PayoutRequestResponse;
import com.buyology.ecommerce.payout.dto.SupplierPayoutAccountResponse;
import com.buyology.ecommerce.payout.enums.PayoutRequestStatus;
import com.buyology.ecommerce.payout.exception.PayoutException;
import com.buyology.ecommerce.payout.repository.PayoutRequestOrderItemRepository;
import com.buyology.ecommerce.payout.repository.PayoutRequestRepository;
import com.buyology.ecommerce.payout.repository.SupplierPayoutAccountRepository;
import com.buyology.ecommerce.supplier.domain.Supplier;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PayoutRequestService {

    private final PayoutRequestRepository requestRepository;
    private final PayoutRequestOrderItemRepository itemRepository;
    private final SupplierPayoutAccountRepository accountRepository;
    private final OrderItemRepository orderItemRepository;
    private final PayoutEligibilityService eligibilityService;
    private final SupplierPayoutAccountService accountService;
    private final ObjectMapper objectMapper;

    public PayoutRequestService(PayoutRequestRepository requestRepository,
                                PayoutRequestOrderItemRepository itemRepository,
                                SupplierPayoutAccountRepository accountRepository,
                                OrderItemRepository orderItemRepository,
                                PayoutEligibilityService eligibilityService,
                                SupplierPayoutAccountService accountService,
                                ObjectMapper objectMapper) {
        this.requestRepository = requestRepository;
        this.itemRepository = itemRepository;
        this.accountRepository = accountRepository;
        this.orderItemRepository = orderItemRepository;
        this.eligibilityService = eligibilityService;
        this.accountService = accountService;
        this.objectMapper = objectMapper;
    }

    // ── Supplier ─────────────────────────────────────────────────────────────

    @Transactional
    public PayoutRequestResponse submitForCurrentSupplier() {
        Supplier supplier = accountService.requireCurrentSupplier();
        PayoutEligibilityResponse eligibility = eligibilityService.compute(supplier.getId());
        if (!eligibility.eligible()) {
            throw new PayoutException(eligibility.reason());
        }

        SupplierPayoutAccount account = accountRepository.findBySupplierId(supplier.getId())
                .orElseThrow(() -> new PayoutException("Add your payout details to request a payout"));

        List<OrderItem> items = orderItemRepository.findPayoutEligibleBySupplierId(supplier.getId());
        BigDecimal amount = items.stream()
                .map(OrderItem::getTotalPrice)
                .filter(p -> p != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (amount.signum() <= 0) {
            throw new PayoutException("No revenue is currently available for payout");
        }

        PayoutRequest req = new PayoutRequest();
        req.setSupplierId(supplier.getId());
        req.setAmountAed(amount);
        req.setStatus(PayoutRequestStatus.PENDING);
        req.setAccountSnapshotJson(serializeSnapshot(SupplierPayoutAccountResponse.from(account)));
        req = requestRepository.save(req);

        UUID requestId = req.getId();
        for (OrderItem item : items) {
            PayoutRequestOrderItem link = new PayoutRequestOrderItem();
            link.setPayoutRequestId(requestId);
            link.setOrderItemId(item.getId());
            itemRepository.save(link);
        }
        return toResponse(req);
    }

    @Transactional(readOnly = true)
    public Page<PayoutRequestResponse> listForCurrentSupplier(int page, int size) {
        Supplier supplier = accountService.requireCurrentSupplier();
        return requestRepository.findAllBySupplierId(
                supplier.getId(),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).map(this::toResponse);
    }

    // ── Admin ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<PayoutRequestResponse> listForAdmin(PayoutRequestStatus status, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PayoutRequest> result = (status == null)
                ? requestRepository.findAll(pageable)
                : requestRepository.findAllByStatus(status, pageable);
        return result.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public PayoutRequestResponse getForAdmin(UUID id) {
        return toResponse(loadOrThrow(id));
    }

    @Transactional
    public PayoutRequestResponse markPaid(UUID id, UUID adminId, String note) {
        PayoutRequest req = loadOrThrow(id);
        if (req.getStatus() != PayoutRequestStatus.PENDING) {
            throw new PayoutException("Only pending payout requests can be marked paid");
        }
        req.setStatus(PayoutRequestStatus.PAID);
        req.setPaidAt(Instant.now());
        req.setPaidByAdminId(adminId);
        if (note != null && !note.isBlank()) req.setAdminNote(note);
        return toResponse(requestRepository.save(req));
    }

    @Transactional
    public PayoutRequestResponse reject(UUID id, UUID adminId, String note) {
        if (note == null || note.isBlank()) {
            throw new PayoutException("Rejection note is required");
        }
        PayoutRequest req = loadOrThrow(id);
        if (req.getStatus() != PayoutRequestStatus.PENDING) {
            throw new PayoutException("Only pending payout requests can be rejected");
        }
        req.setStatus(PayoutRequestStatus.REJECTED);
        req.setAdminNote(note);
        req.setPaidByAdminId(adminId);
        return toResponse(requestRepository.save(req));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private PayoutRequest loadOrThrow(UUID id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> new PayoutException("Payout request not found"));
    }

    private PayoutRequestResponse toResponse(PayoutRequest r) {
        List<UUID> ids = itemRepository.findAllByPayoutRequestId(r.getId()).stream()
                .map(PayoutRequestOrderItem::getOrderItemId)
                .toList();
        return PayoutRequestResponse.from(r, ids);
    }

    private String serializeSnapshot(SupplierPayoutAccountResponse a) {
        try {
            return objectMapper.writeValueAsString(a);
        } catch (JsonProcessingException e) {
            throw new PayoutException("Failed to snapshot payout account: " + e.getMessage());
        }
    }
}
