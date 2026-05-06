package com.buyology.ecommerce.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformConfigService {

    public static final String KEY_B2B_PAYBACK_DAYS = "b2b.credit.payback-days";

    private final PlatformConfigRepository repo;

    @Value("${b2b.credit.payback-days:45}")
    private int defaultPaybackDays;

    public PlatformConfigService(PlatformConfigRepository repo) {
        this.repo = repo;
    }

    public int getPaybackDays() {
        return repo.findByKey(KEY_B2B_PAYBACK_DAYS)
                .map(c -> {
                    try {
                        int v = Integer.parseInt(c.getValue());
                        return v > 0 ? v : defaultPaybackDays;
                    } catch (NumberFormatException e) {
                        return defaultPaybackDays;
                    }
                })
                .orElse(defaultPaybackDays);
    }

    @Transactional
    public void setPaybackDays(int days, String updatedBy) {
        if (days < 1 || days > 365) {
            throw new IllegalArgumentException("Payback days must be between 1 and 365");
        }
        PlatformConfig config = repo.findByKey(KEY_B2B_PAYBACK_DAYS)
                .orElseGet(() -> {
                    PlatformConfig c = new PlatformConfig();
                    c.setKey(KEY_B2B_PAYBACK_DAYS);
                    c.setDescription("Days a B2B member has to repay used credit");
                    return c;
                });
        config.setValue(String.valueOf(days));
        config.setUpdatedBy(updatedBy);
        repo.save(config);
    }
}
