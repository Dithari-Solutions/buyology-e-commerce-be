package com.buyology.ecommerce.privacy.controller;

import com.buyology.ecommerce.privacy.service.CookieConsentService;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CookieConsentAuthorizationTest {
    @Configuration @EnableMethodSecurity
    static class Config {
        @Bean CookieConsentService service() { return mock(CookieConsentService.class); }
        @Bean CookieConsentController controller(CookieConsentService service) { return new CookieConsentController(service); }
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void consentHistoryIsNotReadableByCustomersOrMarketingRoles() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var controller = context.getBean(CookieConsentController.class);
            for (String role : List.of("ROLE_CUSTOMER", "ROLE_MARKETING", "ROLE_ADMIN")) {
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("reader", "", List.of(new SimpleGrantedAuthority(role))));
                assertThrows(AccessDeniedException.class, () -> controller.list(0,25,null,null,null));
            }
            verifyNoInteractions(context.getBean(CookieConsentService.class));
        }
    }
    @Test void superadminMayReadTheHistory() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("owner", "", List.of(new SimpleGrantedAuthority("ROLE_SUPERADMIN"))));
            context.getBean(CookieConsentController.class).list(0,25,null,null,null);
            verify(context.getBean(CookieConsentService.class)).list(0,25,null,null,null);
        }
    }
}
