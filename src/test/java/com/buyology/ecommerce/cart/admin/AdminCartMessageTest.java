package com.buyology.ecommerce.cart.admin;

import com.buyology.ecommerce.common.service.EmailService;
import com.buyology.ecommerce.customeremail.service.CustomerEmailGuard;
import com.buyology.ecommerce.infrastructure.external.ContaboObjectService;
import com.buyology.ecommerce.notification.service.PushNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminCartMessageTest {
    final UUID user = UUID.randomUUID(), admin = UUID.randomUUID(), request = UUID.randomUUID();
    JdbcTemplate jdbc;
    EmailService email;
    PushNotificationService push;
    CustomerEmailGuard guard;
    AdminCartActivityService service;
    @BeforeEach void setup() {
        jdbc = mock(JdbcTemplate.class); email = mock(EmailService.class);
        push = mock(PushNotificationService.class); guard = mock(CustomerEmailGuard.class);
        service = new AdminCartActivityService(jdbc, mock(ContaboObjectService.class), email, push, guard, "https://buyology.online");
    }
    Map<String, Object> previous() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", request); row.put("user_id", user); row.put("admin_id", admin);
        row.put("subject", "Your cart"); row.put("body", "Can we help?"); row.put("email_status", "ACCEPTED");
        return row;
    }
    @Test void lostResponseRetryReturnsExistingOutcomeWithoutSendingAgain() {
        when(jdbc.queryForList(anyString(), eq(request))).thenReturn(List.of(previous()));
        assertEquals("ACCEPTED", service.send(user, admin, request, "Your cart", "Can we help?").get("email_status"));
        verifyNoInteractions(email, push, guard);
    }
    @Test void cannotReuseRequestIdForAnotherCustomerOrBody() {
        when(jdbc.queryForList(anyString(), eq(request))).thenReturn(List.of(previous()));
        assertThrows(ResponseStatusException.class, () -> service.send(UUID.randomUUID(), admin, request, "Your cart", "Can we help?"));
        assertThrows(ResponseStatusException.class, () -> service.send(user, admin, request, "Your cart", "Different"));
        verifyNoInteractions(email, push, guard);
    }
    @Test void suppressedOrMissingEmailDoesNotSendEitherChannel() {
        when(jdbc.queryForList(anyString(), eq(request))).thenReturn(List.of());
        when(jdbc.queryForList(anyString(), eq(user))).thenReturn(List.of());
        assertThrows(ResponseStatusException.class, () -> service.send(user, admin, request, "Your cart", "Can we help?"));
        verifyNoInteractions(email, push, guard);
    }
    void eligible() {
        when(jdbc.queryForList(anyString(), eq(request))).thenReturn(List.of());
        when(jdbc.queryForList(anyString(), eq(user))).thenReturn(List.of(Map.of("email", "customer@example.com")));
        when(jdbc.update(startsWith("INSERT INTO admin_cart_messages"), eq(request), eq(user), eq(admin), eq("customer@example.com"), eq("Your cart"), eq("Can we help?"))).thenReturn(1);
        when(jdbc.queryForMap(startsWith("SELECT first_name"), eq(user))).thenReturn(new HashMap<>(Map.of("first_name", "Alice", "email_opt_out_token", UUID.randomUUID())));
        when(jdbc.queryForMap(startsWith("SELECT *"), eq(request))).thenReturn(previous());
    }
    @Test void failedEmailStillCreatesCustomerInboxNotificationAndAttemptsPush() {
        eligible();
        when(email.sendCartActivityEmail(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(false);
        service.send(user, admin, request, "Your cart", "Can we help?");
        verify(jdbc).update(startsWith("UPDATE admin_cart_messages SET email_status"), eq("FAILED"), eq(request));
        verify(jdbc).update(startsWith("INSERT INTO notification_history"), eq(request), eq(user), eq("Your cart"), eq("Can we help?"));
        verify(push).deliverRecordedToUser(eq(user), eq("Your cart"), eq("Can we help?"), argThat(data -> data.get("type").equals("CART_MESSAGE")));
        verify(guard).checkCampaign(admin, 1);
    }
    @Test void acceptedEmailAndRejectedPushRetainRecordedInboxStatus() {
        eligible();
        when(email.sendCartActivityEmail(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(true);
        doThrow(new IllegalStateException("Executor full")).when(push).deliverRecordedToUser(any(), anyString(), anyString(), anyMap());
        service.send(user, admin, request, "Your cart", "Can we help?");
        verify(jdbc).update(startsWith("UPDATE admin_cart_messages SET email_status"), eq("ACCEPTED"), eq(request));
        verify(jdbc, times(2)).update("UPDATE admin_cart_messages SET notification_status = 'RECORDED' WHERE id = ?", request);
        verify(email).sendCartActivityEmail(eq("customer@example.com"), eq("Alice"), eq("Your cart"), eq("Can we help?"), startsWith("https://buyology.online/api/email/opt-out?token="));
    }
    @Test void concurrentDuplicateLosingDatabaseClaimDoesNotSend() {
        eligible();
        when(jdbc.update(startsWith("INSERT INTO admin_cart_messages"), eq(request), eq(user), eq(admin), eq("customer@example.com"), eq("Your cart"), eq("Can we help?"))).thenReturn(0);
        service.send(user, admin, request, "Your cart", "Can we help?");
        verifyNoInteractions(email, push);
    }
}
