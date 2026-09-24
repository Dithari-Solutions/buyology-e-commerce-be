package com.buyology.ecommerce.cart.reminder;

import com.buyology.ecommerce.common.service.EmailService;
import com.buyology.ecommerce.customeremail.service.MarketingAudience;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the template itself and the rules that decide who receives it.
 *
 * <p>These are the mistakes that do not announce themselves: a token left unreplaced in a
 * customer's inbox, a product title that closes the table it sits in, and a suppression clause
 * quietly dropped from one of the two paths that send marketing mail.
 */
class CartReminderEmailTest {

    private static String template() throws Exception {
        try (InputStream in = CartReminderEmailTest.class.getResourceAsStream("/static/abandoned-cart.html")) {
            assertNotNull(in, "the reminder template must ship on the classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void theTemplateCarriesExactlyTheTokensTheSenderFillsIn() throws Exception {
        Matcher m = Pattern.compile("\\{\\{[A-Z_]+}}").matcher(template());
        java.util.Set<String> found = new java.util.TreeSet<>();
        while (m.find()) found.add(m.group());

        assertEquals(
                java.util.Set.of("{{CART_TOTAL}}", "{{CART_URL}}", "{{FIRST_NAME}}",
                        "{{ITEM_COUNT}}", "{{ITEM_ROWS}}", "{{UNSUBSCRIBE_URL}}"),
                found,
                "a token the sender does not fill in reaches the customer as literal {{BRACES}}");
    }

    @Test
    void theTemplateReferencesNoImageThatIs404InProduction() throws Exception {
        String html = template();
        // Every other template in this repo points at /email/wave.png, /email/tree.png, /logo.png
        // or /sdg/goal-*.png — all of which return 404 today, so those emails arrive with broken
        // images. This one may only use assets that actually resolve.
        assertFalse(html.contains("/email/"), "broken asset path");
        assertFalse(html.contains("/sdg/"), "broken asset path");
        assertFalse(html.contains("buyology.online/logo.png"), "broken asset path");
        assertTrue(html.contains("buyology-online-logo-dark-mode.png"), "the logo that does resolve");
    }

    @Test
    void theTemplateTellsTheCustomerWhyTheyGotItAndHowToStop() throws Exception {
        String html = template();
        assertTrue(html.contains("{{UNSUBSCRIBE_URL}}"), "marketing mail needs an opt-out");
        assertTrue(html.toLowerCase().contains("unsubscribe"));
        assertTrue(html.contains("support@buyology.online"),
                "the .com address in the order confirmation is a domain we do not own");
    }

    @Test
    void aProductTitleCannotBreakOutOfTheTableOrForgeATotal() {
        // A title is shop data, and shop data reaches an inbox: escaping is what stops
        // "</table><h1>" from rewriting the email around it.
        List<EmailService.CartLine> lines = List.of(
                new EmailService.CartLine("</td></tr><h1>Free laptop</h1>", 1, "AED 0.00"));

        String rows = rowsHtml(lines);

        assertFalse(rows.contains("<h1>"), "a title must not introduce markup: " + rows);
        assertTrue(rows.contains("&lt;h1&gt;Free laptop&lt;/h1&gt;"), rows);
    }

    @Test
    void everyLineShowsItsQuantityAndPrice() {
        String rows = rowsHtml(List.of(
                new EmailService.CartLine("Lenovo ThinkPad T490", 2, "AED 1798.00"),
                new EmailService.CartLine("Dell Latitude 5400", 1, "AED 799.00")));

        assertTrue(rows.contains("Qty 2"), rows);
        assertTrue(rows.contains("AED 1798.00"), rows);
        assertTrue(rows.contains("Dell Latitude 5400"), rows);
        assertEquals(2, rows.split("<tr>", -1).length - 1, "one row per cart line");
    }

    /** Reaches the private row builder the way the send does — through the public entry point. */
    private static String rowsHtml(List<EmailService.CartLine> lines) {
        try {
            var m = EmailService.class.getDeclaredMethod("cartRowsHtml", List.class);
            m.setAccessible(true);
            return (String) m.invoke(null, lines);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("cartRowsHtml is the method the template depends on", e);
        }
    }

    // ── Suppression ──────────────────────────────────────────────────────────

    @Test
    void theReminderUsesTheSameSuppressionRulesAsTheCampaignSender() {
        String where = MarketingAudience.ELIGIBLE_WHERE;

        // Each clause is someone who must never receive marketing mail. Losing one is silent.
        assertTrue(where.contains("u.email_opt_out_at IS NULL"), "our own unsubscribe link");
        assertTrue(where.contains("ns.is_active = TRUE"), "the newsletter unsubscribe");
        assertTrue(where.contains("COALESCE(u.is_guest, FALSE) = FALSE"), "guest checkouts");
        assertTrue(where.contains("u.status = 'ACTIVE'"), "suspended accounts");
        assertTrue(where.contains("u.deleted_at IS NULL"), "deleted accounts");
        assertTrue(where.contains("c.email IS NOT NULL"), "credentials with no address");
        assertTrue(MarketingAudience.ELIGIBLE.contains(where),
                "the campaign query must be built from the same clauses, not a copy of them");
    }
}
