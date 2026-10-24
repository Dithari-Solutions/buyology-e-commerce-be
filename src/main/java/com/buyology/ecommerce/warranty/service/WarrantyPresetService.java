package com.buyology.ecommerce.warranty.service;

import com.buyology.ecommerce.common.utils.HtmlSanitizer;
import com.buyology.ecommerce.product.repository.ProductRepository;
import com.buyology.ecommerce.warranty.domain.WarrantyPreset;
import com.buyology.ecommerce.warranty.dto.WarrantyPresetRequest;
import com.buyology.ecommerce.warranty.dto.WarrantyPresetResponse;
import com.buyology.ecommerce.warranty.repository.WarrantyPresetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * The named warranty lengths offered in the product form's dropdown.
 *
 * <p>A preset is a category, not a template: products keep pointing at it, so changing its months
 * changes theirs. That is what makes "all laptops now get two years" one edit. Cards already issued
 * are untouched either way — they carry their own dates.
 */
@Service
public class WarrantyPresetService {

    static final int MAX_NAME_LENGTH = 100;

    private final WarrantyPresetRepository presets;
    private final ProductRepository products;

    public WarrantyPresetService(WarrantyPresetRepository presets, ProductRepository products) {
        this.presets = presets;
        this.products = products;
    }

    @Transactional(readOnly = true)
    public List<WarrantyPresetResponse> list() {
        Map<UUID, Long> counts = productCounts();
        return presets.findAllByOrderByDurationMonthsAscNameAsc().stream()
                .map(p -> toResponse(p, counts.getOrDefault(p.getId(), 0L)))
                .toList();
    }

    @Transactional
    public WarrantyPresetResponse create(WarrantyPresetRequest request) {
        String name = requireName(request);
        int months = ProductWarrantyAssigner.requireValidMonths(request.durationMonths());
        if (presets.existsByNameIgnoreCase(name)) {
            throw new IllegalArgumentException("A warranty preset named \"" + name + "\" already exists");
        }
        WarrantyPreset saved = presets.save(new WarrantyPreset(name, months));
        return toResponse(saved, 0L);
    }

    /** Renames and/or changes the months; products following the preset take the new months. */
    @Transactional
    public WarrantyPresetResponse update(UUID id, WarrantyPresetRequest request) {
        WarrantyPreset preset = find(id);
        String name = requireName(request);
        int months = ProductWarrantyAssigner.requireValidMonths(request.durationMonths());
        if (presets.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new IllegalArgumentException("A warranty preset named \"" + name + "\" already exists");
        }
        preset.setName(name);
        if (preset.getDurationMonths() != months) {
            preset.setDurationMonths(months);
            products.updateWarrantyMonthsForPreset(id, months, Instant.now());
        }
        WarrantyPreset saved = presets.save(preset);
        return toResponse(saved, productCounts().getOrDefault(id, 0L));
    }

    /**
     * Deletes the preset. Its products keep their months as a custom duration rather than losing
     * their warranty: deleting a dropdown entry should not quietly stop cards being issued.
     *
     * @return how many products were switched to a custom duration
     */
    @Transactional
    public int delete(UUID id) {
        WarrantyPreset preset = find(id);
        int detached = products.detachWarrantyPreset(id, Instant.now());
        presets.delete(preset);
        return detached;
    }

    private WarrantyPreset find(UUID id) {
        return presets.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Warranty preset not found with id: " + id));
    }

    private static String requireName(WarrantyPresetRequest request) {
        String name = request.name() == null ? "" : HtmlSanitizer.stripHtml(request.name()).trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Preset name is required");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Preset name must be at most " + MAX_NAME_LENGTH + " characters");
        }
        return name;
    }

    private Map<UUID, Long> productCounts() {
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : products.countByWarrantyPreset()) {
            counts.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    private static WarrantyPresetResponse toResponse(WarrantyPreset p, long productCount) {
        return new WarrantyPresetResponse(p.getId(), p.getName(), p.getDurationMonths(), productCount,
                p.getCreatedAt(), p.getUpdatedAt());
    }
}
