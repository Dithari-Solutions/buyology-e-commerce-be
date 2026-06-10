package com.buyology.ecommerce.refund.service;

import com.buyology.ecommerce.refund.domain.RefundSetting;
import com.buyology.ecommerce.refund.dto.PublicRefundSettingResponse;
import com.buyology.ecommerce.refund.dto.RefundSettingResponse;
import com.buyology.ecommerce.refund.dto.UpdateRefundSettingRequest;
import com.buyology.ecommerce.refund.repository.RefundSettingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class RefundSettingService {

    private final RefundSettingRepository repository;
    private final int defaultWindowDays;
    private final BigDecimal defaultCourierFeeAed;

    public RefundSettingService(
            RefundSettingRepository repository,
            @Value("${refund.default.window-days:14}") int defaultWindowDays,
            @Value("${refund.default.courier-fee-aed:15.00}") BigDecimal defaultCourierFeeAed) {
        this.repository = repository;
        this.defaultWindowDays = defaultWindowDays;
        this.defaultCourierFeeAed = defaultCourierFeeAed;
    }

    /**
     * Returns the singleton settings row, seeding defaults on first access.
     */
    @Transactional
    public RefundSetting getOrCreate() {
        List<RefundSetting> all = repository.findAll();
        if (!all.isEmpty()) return all.get(0);

        RefundSetting seeded = new RefundSetting();
        seeded.setRefundWindowDays(defaultWindowDays);
        seeded.setCourierFeeAed(defaultCourierFeeAed);
        seeded.setCourierFeeCurrency("AED");
        seeded.setEnabled(true);
        return repository.save(seeded);
    }

    @Transactional
    public RefundSettingResponse update(UpdateRefundSettingRequest request, UUID adminId) {
        RefundSetting setting = getOrCreate();
        setting.setRefundWindowDays(request.refundWindowDays());
        setting.setCourierFeeAed(request.courierFeeAed());
        if (request.enabled() != null) setting.setEnabled(request.enabled());
        setting.setUpdatedBy(adminId);
        RefundSetting saved = repository.save(setting);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public RefundSettingResponse getResponse() {
        return toResponse(getOrCreate());
    }

    /**
     * Storefront-facing settings: the return/refund window in days and whether returns
     * are enabled. Same window value the admin configures, with admin-only fields stripped.
     */
    @Transactional(readOnly = true)
    public PublicRefundSettingResponse getPublicResponse() {
        RefundSetting s = getOrCreate();
        return new PublicRefundSettingResponse(s.getRefundWindowDays(), s.isEnabled());
    }

    public RefundSettingResponse toResponse(RefundSetting s) {
        return new RefundSettingResponse(
                s.getId(), s.getRefundWindowDays(), s.getCourierFeeAed(),
                s.getCourierFeeCurrency(), s.isEnabled(), s.getUpdatedBy(), s.getUpdatedAt()
        );
    }
}
