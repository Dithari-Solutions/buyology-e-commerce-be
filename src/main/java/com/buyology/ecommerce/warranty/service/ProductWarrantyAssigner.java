package com.buyology.ecommerce.warranty.service;

import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.warranty.domain.WarrantyPreset;
import com.buyology.ecommerce.warranty.repository.WarrantyPresetRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Writes a product's warranty from what the product form sent: a preset, or a typed number of
 * months.
 *
 * <p>The API leaves both optional. The dashboard requires a warranty when an admin creates a
 * product, but the importers, the supplier portal and the n8n flow create products too and know
 * nothing of warranties; refusing them would break those paths for a field they cannot fill.
 */
@Component
public class ProductWarrantyAssigner {

    /** Ten years. Also enforced by CHECK constraints in V69. */
    public static final int MAX_MONTHS = 120;

    private final WarrantyPresetRepository presets;

    public ProductWarrantyAssigner(WarrantyPresetRepository presets) {
        this.presets = presets;
    }

    /** A preset wins over typed months; neither leaves the product without a warranty. */
    public void applyOnCreate(Product product, UUID presetId, Integer months) {
        if (presetId != null) {
            followPreset(product, presetId);
        } else if (months != null) {
            setCustom(product, months);
        }
    }

    /**
     * Partial update, like the rest of the product PATCH: nothing sent leaves the warranty alone.
     * Removal is an explicit flag, checked first, because an omitted field cannot say "none".
     * Typed months without a preset switch the product to a custom duration.
     */
    public void applyOnUpdate(Product product, UUID presetId, Integer months, Boolean remove) {
        if (Boolean.TRUE.equals(remove)) {
            product.setWarrantyPresetId(null);
            product.setWarrantyMonths(null);
        } else if (presetId != null) {
            followPreset(product, presetId);
        } else if (months != null) {
            setCustom(product, months);
        }
    }

    private void followPreset(Product product, UUID presetId) {
        WarrantyPreset preset = presets.findById(presetId)
                .orElseThrow(() -> new IllegalArgumentException("Warranty preset not found with id: " + presetId));
        product.setWarrantyPresetId(preset.getId());
        product.setWarrantyMonths(preset.getDurationMonths());
    }

    private static void setCustom(Product product, int months) {
        product.setWarrantyPresetId(null);
        product.setWarrantyMonths(requireValidMonths(months));
    }

    static int requireValidMonths(Integer months) {
        if (months == null || months < 1 || months > MAX_MONTHS) {
            throw new IllegalArgumentException(
                    "Warranty must be between 1 and " + MAX_MONTHS + " months (10 years)");
        }
        return months;
    }
}
