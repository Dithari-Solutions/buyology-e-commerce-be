package com.buyology.ecommerce.partnership;

import com.buyology.ecommerce.common.service.EmailService;
import com.buyology.ecommerce.auth.repository.EmailOtpRepository;
import com.buyology.ecommerce.infrastructure.config.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sendgrid.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PartnershipInvitationEmailTest {
    EmailService service;
    SendGrid provider;
    @BeforeEach void setup() {
        var props = new TwilioSendGridProperties(); props.setApiKey("test-only"); props.setFromEmail("partners@example.com"); props.setFromName("Buyology");
        service = new EmailService(mock(EmailOtpRepository.class), props, mock(OtpProperties.class));
        provider = mock(SendGrid.class); ReflectionTestUtils.setField(service, "sendGrid", provider);
    }
    @Test void previewIsExactlyTheSentEmailWithOnePrivateRecipientAndWorkingCta() throws Exception {
        var preview = new PartnershipInvitationTemplate().preview();
        Response accepted = new Response(); accepted.setStatusCode(202);
        when(provider.api(any())).thenReturn(accepted);
        assertEquals(EmailService.InvitationOutcome.ACCEPTED, service.sendPartnershipInvitationEmail("partner@example.com", preview.subject(), preview.html()));
        var request = org.mockito.ArgumentCaptor.forClass(Request.class); verify(provider).api(request.capture());
        var payload = new ObjectMapper().readTree(request.getValue().getBody());
        assertEquals(1, payload.get("personalizations").size());
        assertEquals(1, payload.get("personalizations").get(0).get("to").size());
        assertEquals("partner@example.com", payload.get("personalizations").get(0).get("to").get(0).get("email").asText());
        assertEquals(preview.html(), payload.get("content").get(0).get("value").asText());
        assertTrue(preview.html().contains("href=\"https://business.buyology.online\""));
        assertTrue(preview.html().contains(">Apply for partnership</a>"));
        assertEquals("support@buyology.online", payload.get("reply_to").get("email").asText());
    }
    @Test void timeoutAndServerErrorsAreUnknownRatherThanClaimedAsSent() throws Exception {
        when(provider.api(any())).thenThrow(new IOException("timeout"));
        assertEquals(EmailService.InvitationOutcome.UNKNOWN, service.sendPartnershipInvitationEmail("a@example.com","subject","html"));
        Response failed = new Response(); failed.setStatusCode(503); doReturn(failed).when(provider).api(any());
        assertEquals(EmailService.InvitationOutcome.UNKNOWN, service.sendPartnershipInvitationEmail("a@example.com","subject","html"));
    }
    @Test void explicitProviderRejectionIsReportedAsFailed() throws Exception {
        Response response = new Response(); response.setStatusCode(400); when(provider.api(any())).thenReturn(response);
        assertEquals(EmailService.InvitationOutcome.FAILED, service.sendPartnershipInvitationEmail("a@example.com","subject","html"));
    }
}
