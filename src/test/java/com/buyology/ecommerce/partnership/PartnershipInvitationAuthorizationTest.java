package com.buyology.ecommerce.partnership;

import com.buyology.ecommerce.role.RbacPolicy;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PartnershipInvitationAuthorizationTest {
    @Configuration @EnableMethodSecurity
    static class Config {
        @Bean PartnershipInvitationService service() { return mock(PartnershipInvitationService.class); }
        @Bean PartnershipInvitationTemplate template() throws Exception { return new PartnershipInvitationTemplate(); }
        @Bean RbacPolicy rbacPolicy() { return new RbacPolicy(false); }
        @Bean PartnershipInvitationController controller(PartnershipInvitationService service, PartnershipInvitationTemplate template) { return new PartnershipInvitationController(service, template); }
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void customersAndUnrelatedStaffCannotPreviewReadOrSendInvitations() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var controller = context.getBean(PartnershipInvitationController.class);
            for (String role : List.of("CUSTOMER", "SUPPLIER", "STORE_ADMIN", "MARKETING")) {
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(UUID.randomUUID(), "", List.of(new SimpleGrantedAuthority("ROLE_" + role))));
                assertThrows(AccessDeniedException.class, controller::preview);
                assertThrows(AccessDeniedException.class, controller::recent);
                assertThrows(AccessDeniedException.class, () -> controller.get(UUID.randomUUID()));
                assertThrows(AccessDeniedException.class, () -> controller.submit(new PartnershipInvitationController.Submission(UUID.randomUUID(), "a@example.com", PartnershipInvitationTemplate.VERSION)));
            }
            verifyNoInteractions(context.getBean(PartnershipInvitationService.class));
        }
    }
    @Test void existingPartnershipStaffCanQueueWithTheirAuthenticatedIdentity() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var controller = context.getBean(PartnershipInvitationController.class);
            var service = context.getBean(PartnershipInvitationService.class);
            for (String role : List.of("SUPERADMIN", "CUSTOMER_SUPPORT", "ADMIN")) {
                UUID admin = UUID.randomUUID(), request = UUID.randomUUID();
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(admin, "", List.of(new SimpleGrantedAuthority("ROLE_" + role))));
                controller.preview(); controller.recent();
                controller.submit(new PartnershipInvitationController.Submission(request, "a@example.com", PartnershipInvitationTemplate.VERSION));
                verify(service).submit(request, admin, "a@example.com", PartnershipInvitationTemplate.VERSION);
            }
        }
    }
}
