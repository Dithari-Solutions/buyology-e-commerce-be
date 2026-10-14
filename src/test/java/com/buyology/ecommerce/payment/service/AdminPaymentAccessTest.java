package com.buyology.ecommerce.payment.service;

import com.buyology.ecommerce.payment.controller.AdminPaymentController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminPaymentAccessTest {
    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean jakarta.persistence.EntityManagerFactory entityManagerFactory() { return mock(jakarta.persistence.EntityManagerFactory.class); }
        @Bean PaymentService paymentService() { return mock(PaymentService.class); }
        @Bean AdminPaymentController controller(PaymentService service) { return new AdminPaymentController(service); }
        @Bean(name = "rbacPolicy") Policy policy() { return new Policy(); }
    }
    public static class Policy { public boolean legacyAdmin() { return false; } }
    private final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Config.class);
    @AfterEach void cleanUp() { SecurityContextHolder.clearContext(); context.close(); }
    private void role(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("staff", "", List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    @Test void ordinaryDashboardRolesCannotReadOrConfirmPayments() {
        AdminPaymentController controller = context.getBean(AdminPaymentController.class);
        for (String role : List.of("CUSTOMER_SUPPORT", "STORE_ADMIN", "MARKETING", "SUPPLIER")) {
            role(role);
            assertThrows(AccessDeniedException.class, () -> controller.lookup("36914143"));
            assertThrows(AccessDeniedException.class, () -> controller.confirm("36914143", UUID.randomUUID()));
        }
        verifyNoInteractions(context.getBean(PaymentService.class));
    }

    @Test void superadminCanCheckWithoutConfirmingAndCanConfirmReviewedOrder() {
        role("SUPERADMIN");
        PaymentService service = context.getBean(PaymentService.class);
        AdminPaymentController controller = context.getBean(AdminPaymentController.class);
        UUID order = UUID.randomUUID();
        when(service.lookupPaymobPayment("36914143")).thenReturn(new PaymentService.PaymobPaymentLookup("36914143",null,order,"PENDING_PAYMENT","PENDING",true,true,100,"AED","Checked"));
        assertEquals(order, controller.lookup("36914143").getBody().getData().orderId());
        verify(service, never()).confirmPaymobPayment(anyString(), any());
        when(service.confirmPaymobPayment("36914143", order)).thenReturn(new PaymentService.RecheckResult(true,"SUCCESS","Confirmed"));
        assertTrue(controller.confirm("36914143", order).getBody().getData().settled());
    }
}
