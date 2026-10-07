package com.buyology.ecommerce.cart.admin;

import com.buyology.ecommerce.auth.repository.EmailOtpRepository;
import com.buyology.ecommerce.common.service.EmailService;
import com.buyology.ecommerce.infrastructure.config.OtpProperties;
import com.buyology.ecommerce.infrastructure.config.TwilioSendGridProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CartActivityEmailTest {
    EmailService service() {
        var config = new TwilioSendGridProperties();
        config.setApiKey("test-only"); config.setFromEmail("support@example.com"); config.setFromName("Buyology");
        return new EmailService(mock(EmailOtpRepository.class), config, new OtpProperties());
    }
    @Test void sendsEscapedPlainTextAndUnsubscribeLinkOnlyWhenProviderAccepts() throws Exception {
        Response response = new Response(); response.setStatusCode(202);
        try (var grid = mockConstruction(SendGrid.class, (mock, context) -> when(mock.api(any())).thenReturn(response))) {
            assertTrue(service().sendCartActivityEmail("customer@example.com", "<Alice>", "Your <cart>", "<script>alert(1)</script>\nUnder <100 AED", "https://buyology.online/api/email/opt-out?token=test"));
            var captor = ArgumentCaptor.forClass(Request.class);
            verify(grid.constructed().get(0)).api(captor.capture());
            String html = new ObjectMapper().readTree(captor.getValue().getBody()).get("content").get(0).get("value").asText();
            assertFalse(html.contains("<script>"));
            assertTrue(html.contains("&lt;script&gt;"));
            assertTrue(html.contains("&lt;Alice&gt;"));
            assertTrue(html.contains("<br />"));
            assertTrue(html.contains("Unsubscribe"));
        }
    }
    @Test void providerRejectionIsReportedAsFailureInsteadOfSuccess() {
        Response response = new Response(); response.setStatusCode(503); response.setBody("Test unavailable");
        try (var grid = mockConstruction(SendGrid.class, (mock, context) -> when(mock.api(any())).thenReturn(response))) {
            assertFalse(service().sendCartActivityEmail("customer@example.com", "Alice", "Your cart", "Can we help?", "https://buyology.online/api/email/opt-out?token=test"));
            assertEquals(1, grid.constructed().size());
        }
    }
}
