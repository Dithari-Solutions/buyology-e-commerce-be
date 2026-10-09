package com.buyology.ecommerce.partnership;

import com.buyology.ecommerce.common.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PartnershipControllerTest {
    private final PartnershipRequestService service=mock(PartnershipRequestService.class,withSettings().mockMaker("mock-maker-subclass"));
    private final ObjectMapper mapper=new ObjectMapper();
    private final MockMvc mvc=MockMvcBuilders.standaloneSetup(new PartnershipController(service)).setControllerAdvice(new GlobalExceptionHandler()).setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(mapper)).build();
    @Test void returnsOnlyReceiptOnPublicSubmission() throws Exception {
        PartnershipApplication a=PartnershipApplicationTest.valid();
        when(service.submit(any())).thenReturn(new PartnershipRequestService.Receipt(a.requestId()));
        mvc.perform(post("/api/partnership/requests").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(a)))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.data.id").value(a.requestId().toString())).andExpect(jsonPath("$.data.application").doesNotExist());
    }
    @Test void incompleteBodyCannotReachPersistence() throws Exception {
        mvc.perform(post("/api/partnership/requests").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void referenceConflictIsNotConvertedToInternalServerError() throws Exception {
        when(service.submit(any())).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT,"Submission reference already used"));
        mvc.perform(post("/api/partnership/requests").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(PartnershipApplicationTest.valid())))
            .andExpect(status().isConflict());
    }
}
