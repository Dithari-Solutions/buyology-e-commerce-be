package com.buyology.ecommerce.partnership;

import com.buyology.ecommerce.common.service.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PartnershipRequestServiceTest {
    private final PartnershipRequestRepository repository=mock(PartnershipRequestRepository.class,withSettings().mockMaker("mock-maker-subclass"));
    private final EmailService emails=mock(EmailService.class,withSettings().mockMaker("mock-maker-subclass"));
    private final ObjectMapper mapper=new ObjectMapper();
    private final PartnershipRequestService service=new PartnershipRequestService(repository,mapper,emails);
    private PartnershipRequest stored(PartnershipApplication application) throws Exception {
        PartnershipRequest request=new PartnershipRequest();
        ReflectionTestUtils.setField(request,"id",application.requestId());
        ReflectionTestUtils.setField(request,"payload",mapper.writeValueAsString(application));
        ReflectionTestUtils.setField(request,"createdAt",Instant.now());
        return request;
    }
    @Test void retriesReturnSameReceiptAndDoNotSendBeforeCommit() throws Exception {
        PartnershipApplication a=PartnershipApplicationTest.valid();
        when(repository.findById(a.requestId())).thenReturn(Optional.of(stored(a)));
        assertEquals(a.requestId(),service.submit(a).id()); assertEquals(a.requestId(),service.submit(a).id());
        verify(repository,times(2)).insertOnce(eq(a.requestId()),anyString());
        verifyNoInteractions(emails);
    }
    @Test void sameReferenceCannotOverwriteDifferentApplication() throws Exception {
        PartnershipApplication a=PartnershipApplicationTest.valid();
        when(repository.findById(a.requestId())).thenReturn(Optional.of(stored(a)));
        PartnershipApplication other=new PartnershipApplication(a.requestId(),"Another Name",a.company(),a.cityCountry(),a.phone(),a.email(),a.website(),a.answers(),a.investment(),a.partnerships(),"");
        assertThrows(ResponseStatusException.class,()->service.submit(other)); verifyNoInteractions(emails);
    }
    @Test void providerFailureRetainsSavedApplicationAndRetriesThenReportsFailure() throws Exception {
        PartnershipApplication a=PartnershipApplicationTest.valid(); PartnershipRequest request=stored(a);
        when(repository.lockPendingEmails()).thenReturn(List.of(request));
        when(emails.sendPartnershipReceivedEmail(anyString(),anyString(),anyString(),anyString())).thenReturn(false);
        service.sendPendingEmails(); assertEquals("PENDING",request.getEmailStatus()); assertEquals(1,request.getEmailAttempts());
        for(int i=1;i<5;i++) service.sendPendingEmails();
        assertEquals("FAILED",request.getEmailStatus()); assertEquals(5,request.getEmailAttempts()); assertEquals(a,mapper.readValue(request.getPayload(),PartnershipApplication.class));
    }
    @Test void providerAcceptanceIsRecorded() throws Exception {
        PartnershipApplication a=PartnershipApplicationTest.valid(); PartnershipRequest request=stored(a);
        when(repository.lockPendingEmails()).thenReturn(List.of(request));
        when(emails.sendPartnershipReceivedEmail(a.email(),a.name(),a.company(),a.requestId().toString())).thenReturn(true);
        service.sendPendingEmails(); assertEquals("SENT",request.getEmailStatus()); assertEquals(1,request.getEmailAttempts());
    }
}
