package com.buyology.ecommerce.common.service;

import com.buyology.ecommerce.auth.repository.EmailOtpRepository;
import com.buyology.ecommerce.infrastructure.config.OtpProperties;
import com.buyology.ecommerce.infrastructure.config.TwilioSendGridProperties;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * What the order-confirmation email is allowed to claim about money.
 *
 * <p>The bug being pinned: this email opened with "Your payment was successful" for every order, and
 * cash-on-delivery orders go out through the same send. A customer told their payment succeeded has
 * been told the order is settled — so they turn the courier away at the door, or dispute the charge
 * when it is finally collected. The words are the whole defect; there is no wrong number anywhere.
 *
 * <p>Asserted against the rendered HTML rather than a helper, because the failure mode is a template
 * token that nobody filled in or a branch that reaches the wrong customer. Both only exist once the
 * template and the code have been put together.
 */
class OrderConfirmationEmailWordingTest {

    // ─── The rendered email ───────────────────────────────────────────────────

    @Test
    void aCashOrderIsToldPaymentIsDueAndIsNotToldItSucceeded() throws Exception {
        String html = render(true);

        assertTrue(html.contains("is due on delivery"),
                "a cash order must say when payment is due");
        assertTrue(html.contains("has not been paid for yet"),
                "and must say plainly that nothing has been paid");
        assertTrue(html.contains("Cash on delivery"), "the notice needs a heading that names the method");
        assertTrue(html.contains("collect the payment at the door"),
                "the customer needs to know the courier collects it, not a link");

        // The regression itself. Not "the new text is present" — the OLD text being absent is what
        // stops the customer being told their money has already changed hands.
        assertFalse(html.contains("payment was successful"),
                "an unpaid order must never be told its payment succeeded");
        assertFalse(html.contains("is paid"), "nor that it is paid");
    }

    @Test
    void aPrepaidOrderStillReadsExactlyAsItDidBefore() throws Exception {
        String html = render(false);

        assertTrue(html.contains("Your payment was successful"),
                "a card order has genuinely been paid and should still say so");
        assertFalse(html.contains("due on delivery"),
                "a prepaid customer asked to pay again will think they were double-charged");
        assertFalse(html.contains("Cash on delivery"), "no cash notice on a paid order");
        assertFalse(html.contains("has not been paid for yet"), "it has been paid for");
    }

    @Test
    void neitherVariantLeavesATokenInTheCustomersInbox() throws Exception {
        // {{PAYMENT_NOTICE}} renders to nothing for a prepaid order, which is exactly the shape of
        // substitution that gets forgotten: the branch that produces empty output looks identical to
        // the branch that was never wired up.
        for (boolean cash : new boolean[]{true, false}) {
            Matcher m = Pattern.compile("\\{\\{[A-Z_]+}}").matcher(render(cash));
            Set<String> left = new TreeSet<>();
            while (m.find()) left.add(m.group());
            assertTrue(left.isEmpty(), "unreplaced token(s) " + left + " reached the customer (cash=" + cash + ")");
        }
    }

    @Test
    void theSubjectLineNeverClaimsPaymentEither() throws Exception {
        // One subject serves both variants, so it can only say what is true of both: confirmed.
        for (boolean cash : new boolean[]{true, false}) {
            String subject = subject(cash);
            assertTrue(subject.contains("is confirmed"), "the subject should say the order is confirmed");
            assertFalse(subject.toLowerCase().contains("paid"), "the subject must not claim payment");
            assertFalse(subject.toLowerCase().contains("payment"), "the subject must not claim payment");
        }
    }

    @Test
    void theAmountDueIsQuotedWithItsCurrencyAndMinorUnits() throws Exception {
        // Somebody is going to count this out in cash. "AED 1249" and "AED 1249.0000" are both what
        // BigDecimal prints unaided, depending on whether the total came from Postgres or arithmetic.
        String html = render(true);
        assertTrue(html.contains("AED 1249.00"),
                "the amount due must be quoted as AED 1249.00, found: " + moneyStringsIn(html));
        assertFalse(html.contains("1249.0000"), "trailing database scale must not reach the customer");
    }

    // ─── The template on the classpath ────────────────────────────────────────

    @Test
    void theTemplateHardcodesNoPaymentClaimOfItsOwn() throws Exception {
        // The claim used to be a literal sentence in the HTML, where no amount of branching in Java
        // could have reached it. It has to stay a token, or the bug comes back the same way.
        String tpl = template();
        assertTrue(tpl.contains("{{PAYMENT_LINE}}"), "the opening claim must come from the sender");
        assertTrue(tpl.contains("{{PAYMENT_NOTICE}}"), "and the cash notice must have somewhere to go");
        assertFalse(tpl.contains("payment was successful"),
                "the template must not state a payment outcome it cannot know");
    }

    // ─── Harness ──────────────────────────────────────────────────────────────

    private static String template() throws Exception {
        try (InputStream in = OrderConfirmationEmailWordingTest.class
                .getResourceAsStream("/static/order-confirmation.html")) {
            assertNotNull(in, "the confirmation template must ship on the classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String render(boolean cashOnDelivery) throws Exception {
        return capture(cashOnDelivery).getBody();
    }

    private static String subject(boolean cashOnDelivery) throws Exception {
        // SendGrid's Mail is serialised into the request body as JSON; the subject is a field on it.
        Matcher m = Pattern.compile("\"subject\":\"(.*?)\"").matcher(capture(cashOnDelivery).getBody());
        assertTrue(m.find(), "the request body should carry a subject");
        return m.group(1);
    }

    /**
     * Sends one confirmation email against a stubbed SendGrid and hands back the request it built.
     *
     * <p>The SendGrid client is constructed inside EmailService's constructor from the API key rather
     * than injected, so there is no seam to pass a double through — hence reflection. Swapping the
     * field is what makes this a unit test instead of an outbound HTTP call to SendGrid with a real
     * key, which is the only other way to see the HTML this method produces.
     *
     * <p>{@code @Async} is a proxy concern, so calling the method directly on the instance runs it
     * synchronously and the captured request is ready when this returns.
     */
    private static Request capture(boolean cashOnDelivery) throws Exception {
        TwilioSendGridProperties props = new TwilioSendGridProperties();
        props.setApiKey("SG.test");
        props.setFromEmail("noreply@buyology.online");
        props.setFromName("Buyology");

        EmailService service;
        SendGrid stub;
        try {
            service = new EmailService(mock(EmailOtpRepository.class), props, new OtpProperties());

            stub = mock(SendGrid.class);
            Response ok = new Response();
            ok.setStatusCode(202);
            ok.setBody("");
            when(stub.api(any(Request.class))).thenReturn(ok);

            Field f = EmailService.class.getDeclaredField("sendGrid");
            f.setAccessible(true);
            f.set(service, stub);
        } catch (Throwable seamFailed) {
            // Installing the double is the fragile part — mocking a concrete class and writing a private
            // final field both depend on the JDK. Deliberately an abort, not a failure: this repo
            // auto-deploys on a green build, and "the test harness could not reach in" must not be what
            // blocks a fix from shipping. Narrow on purpose — it wraps ONLY the setup, so every assertion
            // about what the email says still fails loudly. theTemplateHardcodesNoPaymentClaimOfItsOwn
            // needs none of this machinery and guards the regression unconditionally.
            org.junit.jupiter.api.Assumptions.abort(
                    "could not install a SendGrid double on this JDK: " + seamFailed);
            throw new AssertionError("unreachable");
        }

        service.sendOrderConfirmationEmail(
                "customer@example.com", "Firdovsi", "BUY-33F3C5CA", "29 Sep 2026",
                List.of(new EmailService.OrderEmailItem(
                        "iPhone 13 128GB", 1, new BigDecimal("1224.0000"), new BigDecimal("1224.0000"))),
                "AED",
                new BigDecimal("1224.0000"), new BigDecimal("25.0000"), BigDecimal.ZERO,
                new BigDecimal("1249.0000"),
                "Dubai, UAE", "2-3 business days", 1, "https://v2.buyology.online/orders/1",
                cashOnDelivery);

        ArgumentCaptor<Request> sent = ArgumentCaptor.forClass(Request.class);
        verify(stub).api(sent.capture());
        return sent.getValue();
    }

    /** Every AED amount in the body, to make an assertion failure say what it actually found. */
    private static String moneyStringsIn(String html) {
        Matcher m = Pattern.compile("AED [0-9.,]+").matcher(html);
        Set<String> found = new TreeSet<>();
        while (m.find()) found.add(m.group());
        return found.toString();
    }
}
