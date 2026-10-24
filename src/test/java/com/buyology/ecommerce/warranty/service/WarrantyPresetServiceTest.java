package com.buyology.ecommerce.warranty.service;

import com.buyology.ecommerce.product.repository.ProductRepository;
import com.buyology.ecommerce.warranty.domain.WarrantyPreset;
import com.buyology.ecommerce.warranty.dto.WarrantyPresetRequest;
import com.buyology.ecommerce.warranty.repository.WarrantyPresetRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WarrantyPresetServiceTest {

    private final WarrantyPresetRepository presets = mock(WarrantyPresetRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final WarrantyPresetService service = new WarrantyPresetService(presets, products);

    private WarrantyPreset stored(String name, int months) {
        WarrantyPreset p = new WarrantyPreset(name, months);
        p.setId(UUID.randomUUID());
        when(presets.findById(p.getId())).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void createsATrimmedPresetWithNoProductsYet() {
        when(presets.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var created = service.create(new WarrantyPresetRequest("  Laptop  ", 12));
        assertEquals("Laptop", created.name());
        assertEquals(12, created.durationMonths());
        assertEquals(0, created.productCount());
    }

    @Test
    void refusesADuplicateNameWhateverItsCase() {
        when(presets.existsByNameIgnoreCase("laptop")).thenReturn(true);
        var e = assertThrows(IllegalArgumentException.class,
                () -> service.create(new WarrantyPresetRequest("laptop", 12)));
        assertTrue(e.getMessage().contains("already exists"));
        verify(presets, never()).save(any());
    }

    @Test
    void refusesAMissingNameOrAnImpossibleLength() {
        assertThrows(IllegalArgumentException.class, () -> service.create(new WarrantyPresetRequest(" ", 12)));
        assertThrows(IllegalArgumentException.class, () -> service.create(new WarrantyPresetRequest(null, 12)));
        assertThrows(IllegalArgumentException.class, () -> service.create(new WarrantyPresetRequest("Laptop", null)));
        assertThrows(IllegalArgumentException.class, () -> service.create(new WarrantyPresetRequest("Laptop", 0)));
        assertThrows(IllegalArgumentException.class,
                () -> service.create(new WarrantyPresetRequest("x".repeat(101), 12)));
    }

    @Test
    void changingTheLengthMovesEveryProductThatFollowsIt() {
        WarrantyPreset laptop = stored("Laptop", 12);
        when(presets.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(products.countByWarrantyPreset()).thenReturn(List.<Object[]>of(new Object[]{laptop.getId(), 7L}));

        var updated = service.update(laptop.getId(), new WarrantyPresetRequest("Laptop", 24));

        verify(products).updateWarrantyMonthsForPreset(eq(laptop.getId()), eq(24), any());
        assertEquals(24, updated.durationMonths());
        assertEquals(7, updated.productCount());
    }

    @Test
    void renamingAloneDoesNotTouchProducts() {
        WarrantyPreset laptop = stored("Laptop", 12);
        when(presets.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service.update(laptop.getId(), new WarrantyPresetRequest("Laptops", 12));
        verify(products, never()).updateWarrantyMonthsForPreset(any(), anyInt(), any());
        assertEquals("Laptops", laptop.getName());
    }

    @Test
    void renamingOntoAnotherPresetsNameIsRefused() {
        WarrantyPreset laptop = stored("Laptop", 12);
        when(presets.existsByNameIgnoreCaseAndIdNot("Accessory", laptop.getId())).thenReturn(true);
        assertThrows(IllegalArgumentException.class,
                () -> service.update(laptop.getId(), new WarrantyPresetRequest("Accessory", 12)));
    }

    @Test
    void deletingKeepsProductsLengthsAndReportsHowManyWereSwitched() {
        WarrantyPreset laptop = stored("Laptop", 12);
        when(products.detachWarrantyPreset(eq(laptop.getId()), any())).thenReturn(3);

        assertEquals(3, service.delete(laptop.getId()));

        verify(products).detachWarrantyPreset(eq(laptop.getId()), any());
        verify(presets).delete(laptop);
    }

    @Test
    void anUnknownPresetIsNotFound() {
        UUID missing = UUID.randomUUID();
        when(presets.findById(missing)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> service.delete(missing));
        assertThrows(NoSuchElementException.class,
                () -> service.update(missing, new WarrantyPresetRequest("Laptop", 12)));
    }

    @Test
    void listsWithEachPresetsProductCount() {
        WarrantyPreset accessory = new WarrantyPreset("Accessory", 6);
        accessory.setId(UUID.randomUUID());
        WarrantyPreset laptop = new WarrantyPreset("Laptop", 12);
        laptop.setId(UUID.randomUUID());
        when(presets.findAllByOrderByDurationMonthsAscNameAsc()).thenReturn(List.of(accessory, laptop));
        when(products.countByWarrantyPreset()).thenReturn(List.<Object[]>of(new Object[]{laptop.getId(), 4L}));

        var list = service.list();

        assertEquals(List.of("Accessory", "Laptop"), list.stream().map(p -> p.name()).toList());
        assertEquals(0, list.get(0).productCount());
        assertEquals(4, list.get(1).productCount());
    }
}
