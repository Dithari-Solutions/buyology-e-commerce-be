package com.buyology.ecommerce.warranty.controller;

import com.buyology.ecommerce.warranty.dto.PublicWarrantyResponse;
import com.buyology.ecommerce.warranty.dto.VoidWarrantyRequest;
import com.buyology.ecommerce.warranty.service.WarrantyCardService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Routes and guards, checked without a running application (the one context-loading test in this
 * repo needs a database). The public lookup must stay open; everything else must stay guarded.
 */
class WarrantyControllerWiringTest {

    private static String guard(Class<?> controller, String method) {
        Method m = Arrays.stream(controller.getDeclaredMethods())
                .filter(x -> x.getName().equals(method)).findFirst().orElseThrow();
        PreAuthorize pre = m.getAnnotation(PreAuthorize.class);
        return pre == null ? null : pre.value();
    }

    @Test
    void theLookupIsPublicAtTheAddressDithariCalls() throws Exception {
        assertArrayEquals(new String[]{"/api/warranty"},
                PublicWarrantyController.class.getAnnotation(RequestMapping.class).value());
        Method lookup = PublicWarrantyController.class.getMethod("lookup", String.class);
        assertArrayEquals(new String[]{"/{serial}"}, lookup.getAnnotation(GetMapping.class).value());
        assertNull(lookup.getAnnotation(PreAuthorize.class));
        assertNull(PublicWarrantyController.class.getAnnotation(PreAuthorize.class));
    }

    @Test
    void theLookupHandsTheSerialToTheServiceAsTyped() {
        WarrantyCardService service = mock(WarrantyCardService.class);
        PublicWarrantyResponse card = new PublicWarrantyResponse("DTH-7K4Q-2M9X-QH3T", "ACTIVE", 12,
                "2026-06-24", "2027-06-24", "Buyology", null,
                new PublicWarrantyResponse.Product("Dell Latitude 7420", "Dell", "Laptop", "New", List.of()));
        when(service.lookup("dth 7k4q 2m9x qh3t")).thenReturn(card);

        var response = new PublicWarrantyController(service).lookup("dth 7k4q 2m9x qh3t");

        assertEquals(200, response.getStatusCode().value());
        assertSame(card, response.getBody().getData());
    }

    @Test
    void presetsAreReadableByProductEditorsAndChangedOnlyByPresetManagers() {
        assertArrayEquals(new String[]{"/api/admin/warranty-presets"},
                AdminWarrantyPresetController.class.getAnnotation(RequestMapping.class).value());
        String list = guard(AdminWarrantyPresetController.class, "list");
        assertTrue(list.contains("hasAuthority('product:warranty:read')"));
        assertTrue(list.contains("hasAuthority('product:create')"), "the product form needs the dropdown");
        assertTrue(list.contains("hasAuthority('product:update')"));
        assertTrue(guard(AdminWarrantyPresetController.class, "create").contains("'product:warranty:create'"));
        assertTrue(guard(AdminWarrantyPresetController.class, "update").contains("'product:warranty:update'"));
        assertTrue(guard(AdminWarrantyPresetController.class, "delete").contains("'product:warranty:delete'"));
    }

    @Test
    void voidingAndRestoringNeedTheOrderWarrantyPermission() {
        assertTrue(guard(AdminWarrantyCardController.class, "voidCard").contains("'order:warranty:update'"));
        assertTrue(guard(AdminWarrantyCardController.class, "restoreCard").contains("'order:warranty:update'"));
    }

    @Test
    void voidPassesTheAdminAndReasonThroughAndToleratesNoBody() {
        WarrantyCardService service = mock(WarrantyCardService.class);
        AdminWarrantyCardController controller = new AdminWarrantyCardController(service);
        UUID admin = UUID.randomUUID();

        controller.voidCard(admin, "DTH-7K4Q-2M9X-QH3T", new VoidWarrantyRequest("Returned"));
        controller.voidCard(admin, "DTH-7K4Q-2M9X-QH3T", null);

        verify(service).voidCard("DTH-7K4Q-2M9X-QH3T", "Returned", admin);
        verify(service).voidCard("DTH-7K4Q-2M9X-QH3T", null, admin);
    }
}
