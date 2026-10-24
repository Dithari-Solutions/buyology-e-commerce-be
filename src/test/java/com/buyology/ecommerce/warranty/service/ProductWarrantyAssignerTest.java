package com.buyology.ecommerce.warranty.service;

import com.buyology.ecommerce.product.domain.Product;
import com.buyology.ecommerce.warranty.domain.WarrantyPreset;
import com.buyology.ecommerce.warranty.repository.WarrantyPresetRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductWarrantyAssignerTest {

    private final WarrantyPresetRepository presets = mock(WarrantyPresetRepository.class);
    private final ProductWarrantyAssigner assigner = new ProductWarrantyAssigner(presets);

    private WarrantyPreset preset(int months) {
        WarrantyPreset p = new WarrantyPreset("Laptop", months);
        p.setId(UUID.randomUUID());
        when(presets.findById(p.getId())).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void aPresetCopiesItsMonthsAndIsRemembered() {
        WarrantyPreset laptop = preset(12);
        Product product = new Product();
        assigner.applyOnCreate(product, laptop.getId(), 99);
        assertEquals(12, product.getWarrantyMonths(), "the preset wins over typed months");
        assertEquals(laptop.getId(), product.getWarrantyPresetId());
    }

    @Test
    void typedMonthsAreACustomDuration() {
        Product product = new Product();
        assigner.applyOnCreate(product, null, 18);
        assertEquals(18, product.getWarrantyMonths());
        assertNull(product.getWarrantyPresetId());
    }

    @Test
    void neitherLeavesTheProductWithoutAWarranty() {
        // Importers, the supplier portal and n8n create products without one; that must still work.
        Product product = new Product();
        assigner.applyOnCreate(product, null, null);
        assertNull(product.getWarrantyMonths());
        assertNull(product.getWarrantyPresetId());
    }

    @Test
    void refusesLengthsOutsideOneMonthToTenYears() {
        Product product = new Product();
        assertThrows(IllegalArgumentException.class, () -> assigner.applyOnCreate(product, null, 0));
        assertThrows(IllegalArgumentException.class, () -> assigner.applyOnCreate(product, null, 121));
        assertDoesNotThrow(() -> assigner.applyOnCreate(product, null, 120));
    }

    @Test
    void anUnknownPresetIsABadRequestNotASilentNoWarranty() {
        UUID missing = UUID.randomUUID();
        when(presets.findById(missing)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> assigner.applyOnCreate(new Product(), missing, null));
    }

    @Test
    void updateLeavesTheWarrantyAloneWhenNothingIsSent() {
        WarrantyPreset laptop = preset(12);
        Product product = new Product();
        product.setWarrantyPresetId(laptop.getId());
        product.setWarrantyMonths(12);
        assigner.applyOnUpdate(product, null, null, null);
        assertEquals(12, product.getWarrantyMonths());
        assertEquals(laptop.getId(), product.getWarrantyPresetId());
    }

    @Test
    void updateWithTypedMonthsSwitchesFromPresetToCustom() {
        Product product = new Product();
        product.setWarrantyPresetId(UUID.randomUUID());
        product.setWarrantyMonths(12);
        assigner.applyOnUpdate(product, null, 24, null);
        assertEquals(24, product.getWarrantyMonths());
        assertNull(product.getWarrantyPresetId(), "a custom length no longer follows the preset");
    }

    @Test
    void removalWinsOverAnythingSentWithIt() {
        WarrantyPreset laptop = preset(12);
        Product product = new Product();
        product.setWarrantyMonths(6);
        assigner.applyOnUpdate(product, laptop.getId(), 24, true);
        assertNull(product.getWarrantyMonths());
        assertNull(product.getWarrantyPresetId());
    }
}
