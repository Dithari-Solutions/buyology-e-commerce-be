package com.buyology.ecommerce.partnership;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PartnershipApplicationTest {
    private final Validator validator=Validation.buildDefaultValidatorFactory().getValidator();
    static PartnershipApplication application(Map<String,Boolean> answers, List<String> partnerships, String investment, String website, String trap) {
        return new PartnershipApplication(UUID.randomUUID(),"Partner Name","Partner Company","Baku / Azerbaijan","+994 50 123 4567","partner@example.com",website,answers,investment,partnerships,"Buyology offers the products and partnership support our business needs.",trap);
    }
    static Map<String,Boolean> answers() { Map<String,Boolean> result=new HashMap<>(); PartnershipApplication.ANSWER_KEYS.forEach(k->result.put(k,false)); return result; }
    static PartnershipApplication valid() { return application(answers(),List.of("STOCKIST"),"USD 50,000–75,000",null,""); }
    @Test void allNoAnswersAndOptionalWebsiteAreValid() { assertTrue(validator.validate(valid()).isEmpty()); }
    @Test void missingFinalConfirmationIsRejected() { Map<String,Boolean> a=answers(); a.remove("finalDiscussion"); assertFalse(validator.validate(application(a,List.of("STOCKIST"),"USD 50,000–75,000",null,"")).isEmpty()); }
    @Test void nullAnswerCannotBeMistakenForNo() { Map<String,Boolean> a=answers(); a.put("storage",null); assertFalse(validator.validate(application(a,List.of("STOCKIST"),"USD 50,000–75,000",null,"")).isEmpty()); }
    @Test void unknownQuestionCannotReplaceRequiredQuestion() { Map<String,Boolean> a=answers(); a.remove("team"); a.put("unknown",true); assertFalse(validator.validate(application(a,List.of("STOCKIST"),"USD 50,000–75,000",null,"")).isEmpty()); }
    @Test void partnershipSelectionRequired() { assertFalse(validator.validate(application(answers(),List.of(),"USD 50,000–75,000",null,"")).isEmpty()); }
    @Test void multiplePartnershipInterestsRejected() { assertFalse(validator.validate(application(answers(),List.of("STOCKIST_RETAILER","FUTURE_TERRITORY"),"Investment available subject to final business plan","@company","")).isEmpty()); }
    @Test void unknownAndDuplicatePartnershipsRejected() { for(List<String> choices:List.of(List.of("UNKNOWN"),List.of("STOCKIST","STOCKIST"))) assertFalse(validator.validate(application(answers(),choices,"USD 50,000–75,000",null,"")).isEmpty()); }
    @Test void invalidInvestmentAndHoneypotRejected() { assertFalse(validator.validate(application(answers(),List.of("STOCKIST"),"USD 1",null,"")).isEmpty()); assertFalse(validator.validate(application(answers(),List.of("STOCKIST"),"USD 50,000–75,000",null,"spam")).isEmpty()); }
    @Test void allBasicContactFieldsRequired() { PartnershipApplication a=valid(); assertFalse(validator.validate(new PartnershipApplication(a.requestId(),"","","","","",null,a.answers(),a.investment(),a.partnerships(),a.whyBuyology(),"")).isEmpty()); }
    @Test void reasonIsRequiredAndBounded() {
        PartnershipApplication a=valid();
        for (String reason : Arrays.asList(null,"","   ","x".repeat(2001))) {
            PartnershipApplication invalid=new PartnershipApplication(a.requestId(),a.name(),a.company(),a.cityCountry(),a.phone(),a.email(),a.website(),a.answers(),a.investment(),a.partnerships(),reason,"");
            assertTrue(validator.validate(invalid).stream().anyMatch(v->v.getPropertyPath().toString().equals("whyBuyology")));
        }
    }
    @Test void payloadRoundTripsWithoutComputedValidationFields() throws Exception { ObjectMapper mapper=new ObjectMapper(); PartnershipApplication a=valid(); String json=mapper.writeValueAsString(a); assertFalse(json.contains("answersComplete")); assertEquals(a,mapper.readValue(json,PartnershipApplication.class)); }
}
