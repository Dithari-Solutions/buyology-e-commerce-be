package com.buyology.ecommerce.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class PaymobIntentionContractTest {
    @Test
    void intentionUsesDocumentedReferencesAndTopLevelCallbackFields() {
        RestTemplate http = new RestTemplate();
        ObjectMapper json = new ObjectMapper();
        MockRestServiceServer server = MockRestServiceServer.bindTo(http).build();
        var items = json.createArrayNode().addObject().put("name","Laptop").put("amount",99900).put("quantity",1);
        server.expect(requestTo("https://uae.paymob.com/v1/intention/"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization","Token secret"))
                .andExpect(jsonPath("$.special_reference").value("local-attempt-id"))
                .andExpect(jsonPath("$.notification_url").value("https://api.buyology.online/api/payments/webhook"))
                .andExpect(jsonPath("$.redirection_url").value("https://buyology.online/payment-status"))
                .andExpect(jsonPath("$.items[0].amount").value(99900))
                .andExpect(jsonPath("$.extras.platform").value("buyology"))
                .andExpect(jsonPath("$.extras.merchant_order_id").value("local-attempt-id"))
                .andExpect(jsonPath("$.order").doesNotExist())
                .andRespond(withSuccess("{\"id\":\"pi_live_test\",\"client_secret\":\"secret-client\",\"intention_order_id\":412345678}",MediaType.APPLICATION_JSON));
        var result = new PaymobClient(http,json).createIntention("secret","https://uae.paymob.com",99900,"AED",123,
                "local-attempt-id",json.createObjectNode(),json.createObjectNode(),json.createArrayNode().add(items),
                "https://api.buyology.online/api/payments/webhook","https://buyology.online/payment-status");
        assertEquals(412345678L,result.paymobOrderId());
        server.verify();
    }
}
