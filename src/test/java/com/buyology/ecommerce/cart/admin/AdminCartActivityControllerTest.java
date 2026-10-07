package com.buyology.ecommerce.cart.admin;

import com.buyology.ecommerce.cart.domain.Cart;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AdminCartActivityControllerTest {
    @Test void cartOutreachRequiresEmailPermissionAndReadEndpointsAreProtected() throws Exception {
        var send = AdminCartActivityController.class.getMethod("send", UUID.class, UUID.class, AdminCartActivityController.MessageRequest.class);
        String policy = send.getAnnotation(PreAuthorize.class).value();
        assertTrue(policy.contains("marketing:email:send"));
        assertFalse(policy.contains("user:read"), "Reading carts must not grant permission to email customers");
        var detail = AdminCartActivityController.class.getMethod("detail", UUID.class);
        assertNotNull(detail.getAnnotation(PreAuthorize.class));
        var list = AdminCartActivityController.class.getMethod("list", int.class, int.class, String.class, boolean.class);
        assertNotNull(list.getAnnotation(PreAuthorize.class));
    }
    @Test void messageRejectsEmptyBodyAndHeaderNewlines() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertFalse(validator.validate(new AdminCartActivityController.MessageRequest(UUID.randomUUID(), "Subject\r\nBcc: x", " ")).isEmpty());
            assertTrue(validator.validate(new AdminCartActivityController.MessageRequest(UUID.randomUUID(), "Your cart", "Can we help?")).isEmpty());
        }
    }
    @Test void normalCartReadsAndLifecycleUpdatesDoNotChangeAdditionTimestamp() {
        Cart cart = new Cart();
        Instant added = Instant.parse("2026-10-07T12:00:00Z");
        cart.setLastAddedAt(added); cart.prePersist(); cart.preUpdate();
        assertEquals(added, cart.getLastAddedAt());
    }
}
