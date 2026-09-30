package com.buyology.ecommerce.store.controller;

import com.buyology.ecommerce.common.response.ApiResponse;
import com.buyology.ecommerce.store.dto.FlashSaleRemoveRequest;
import com.buyology.ecommerce.store.dto.FlashSaleRequest;
import com.buyology.ecommerce.store.service.FlashSaleService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Pins that the flash-sale endpoints are actually REACHABLE and actually reach the service.
 *
 * <p>Why this needed its own test. Every rule the flash sale enforces was covered — the discount
 * arithmetic, the window, the refusals, the paging — and none of it covered the one thing that makes
 * the feature exist for an admin: a route, a permission, and a call to the service behind it. A
 * controller that delegated to the wrong method, dropped a query parameter, or shipped without its
 * {@code @PreAuthorize} would have passed the entire suite while the dashboard screen either 404'd or,
 * worse, let anyone with a session discount the catalogue.
 *
 * <p>Deliberately NOT a {@code @SpringBootTest}: the only context-loading test in this repo needs a
 * database and is the suite's one standing error. These assertions are about wiring, and wiring is
 * visible without a running application.
 */
class FlashSaleControllerWiringTest {

    private final FlashSaleService service = mock(FlashSaleService.class);
    private final FlashSaleController controller = new FlashSaleController(service);

    // ── Every endpoint reaches the service method it advertises ──────────────

    @Test
    void puttingProductsOnSaleDelegatesToTheBatchService() {
        FlashSaleRequest request = new FlashSaleRequest();
        when(service.putOnFlashSale(request)).thenReturn(ApiResponse.success(List.of(), "ok"));

        controller.putOnFlashSale(request);

        verify(service, times(1)).putOnFlashSale(request);
        verifyNoMoreInteractions(service);
    }

    @Test
    void listingPassesTheStoreFilterAndThePageThroughUntouched() {
        // The page and size are the whole reason the admin listing is bounded. A controller that
        // swallowed them would restore the unbounded query one layer above the fix.
        UUID storeId = UUID.randomUUID();
        when(service.getFlashSale(any(), anyInt(), anyInt())).thenReturn(ApiResponse.success(List.of(), "ok"));

        controller.getFlashSale(storeId, 3, 25);

        verify(service).getFlashSale(storeId, 3, 25);
    }

    @Test
    void listingWithNoStoreFilterPassesNullRatherThanInventingAStore() {
        when(service.getFlashSale(any(), anyInt(), anyInt())).thenReturn(ApiResponse.success(List.of(), "ok"));

        controller.getFlashSale(null, 0, 50);

        verify(service).getFlashSale(null, 0, 50);
    }

    @Test
    void removingABatchAndRemovingOneAreDifferentServiceCalls() {
        // They return different shapes and they are two different endpoints; crossing them would take a
        // whole campaign off sale when an admin clicked one row.
        FlashSaleRemoveRequest batch = new FlashSaleRemoveRequest();
        UUID one = UUID.randomUUID();
        when(service.removeFromFlashSale(batch)).thenReturn(ApiResponse.success(List.of(), "ok"));
        when(service.removeOneFromFlashSale(one)).thenReturn(ApiResponse.success(null, "ok"));

        controller.removeFromFlashSale(batch);
        controller.removeOneFromFlashSale(one);

        verify(service).removeFromFlashSale(batch);
        verify(service).removeOneFromFlashSale(one);
    }

    @Test
    void theControllerHoldsNoLogicOfItsOwn() {
        // Everything transactional and every refusal lives in FlashSaleService, which is what the rest of
        // the suite tests. If this controller ever grows a rule, that rule is untested by construction.
        assertEquals(4, java.util.Arrays.stream(FlashSaleController.class.getDeclaredMethods())
                        .filter(m -> m.getReturnType().getName().contains("ResponseEntity"))
                        .count(),
                "four endpoints: put on sale, list, remove batch, remove one");
    }

    // ── The routes and the permissions exist ─────────────────────────────────

    @Test
    void everyEndpointIsMappedAndGuarded() {
        assertEquals("/api/admin/flash-sale",
                FlashSaleController.class.getAnnotation(RequestMapping.class).value()[0]);

        for (Method m : FlashSaleController.class.getDeclaredMethods()) {
            if (!m.getReturnType().getName().contains("ResponseEntity")) continue;
            boolean mapped = m.isAnnotationPresent(GetMapping.class)
                    || m.isAnnotationPresent(PostMapping.class)
                    || m.isAnnotationPresent(DeleteMapping.class);
            assertTrue(mapped, m.getName() + " has no HTTP mapping — the screen would 404");
            assertNotNull(m.getAnnotation(PreAuthorize.class),
                    m.getName() + " has no @PreAuthorize — anyone with a session could reprice the shop");
        }
    }

    @Test
    void theWriteEndpointsRequireTheUpdatePermissionAndTheReadEndpointOnlyRead() {
        // A flash sale IS a store-product price edit, so it reuses those permissions rather than minting
        // new ones nobody's role has been granted. Reading what is on sale must not require write.
        assertTrue(preAuthorizeOf("putOnFlashSale").contains("store:product:update"));
        assertTrue(preAuthorizeOf("removeFromFlashSale").contains("store:product:update"));
        assertTrue(preAuthorizeOf("removeOneFromFlashSale").contains("store:product:update"));

        String listing = preAuthorizeOf("getFlashSale");
        assertTrue(listing.contains("store:product:read"));
        assertFalse(listing.contains("store:product:update"), "listing must not demand write access");
    }

    private String preAuthorizeOf(String methodName) {
        return java.util.Arrays.stream(FlashSaleController.class.getDeclaredMethods())
                .filter(m -> m.getName().equals(methodName))
                .findFirst()
                .map(m -> m.getAnnotation(PreAuthorize.class))
                .map(PreAuthorize::value)
                .orElseThrow(() -> new AssertionError("no such endpoint: " + methodName));
    }

    @Test
    void theGuardIsPerMethodSoANewEndpointCannotInheritOneByAccident() {
        // Deliberately no class-level @PreAuthorize: with one, an endpoint added later would silently
        // pick up whatever the class said instead of failing the assertion above.
        assertNull(FlashSaleController.class.getAnnotation(PreAuthorize.class));
    }
}
