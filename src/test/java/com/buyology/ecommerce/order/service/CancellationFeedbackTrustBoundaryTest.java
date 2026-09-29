package com.buyology.ecommerce.order.service;

import com.buyology.ecommerce.order.controller.OrderController;
import com.buyology.ecommerce.order.domain.Order;
import com.buyology.ecommerce.order.dto.CancellationFeedback;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the boundary between an untrusted client and {@code orders.cancellation_feedback}.
 *
 * <p>This is the one column in the orders table that a shopper writes directly. Anyone who can
 * cancel their own order can put arbitrary strings into it, from a storefront or an app we do not
 * control, and what lands there is rendered in the admin dashboard and counted in the "why do people
 * cancel" numbers the whole flow exists to produce. So there are two distinct ways to fail here, and
 * both are worse than they look:
 *
 * <ul>
 *   <li><b>Storing what the client sent.</b> A 4 MB answer, an embedded newline that splits one
 *       answer into what reads as several records, a client-chosen {@code submittedAt} that makes the
 *       cancellation look like it happened last year, a document claiming a version this backend
 *       never wrote.
 *   <li><b>Letting any of that stop the cancellation.</b> Every cap here truncates and none reject,
 *       because a shopper must never be unable to cancel an order because they typed a long
 *       sentence. The cancellation is the half that matters; the questionnaire is a note attached
 *       to it.
 * </ul>
 */
class CancellationFeedbackTrustBoundaryTest {

    /** A fixed server clock, so an assertion about the stamped time is about the stamp, not the run. */
    private static final Instant NOW = Instant.parse("2026-09-29T10:15:00Z");

    private static CancellationFeedback.Answer answer(String key, String question, String text, String code) {
        return new CancellationFeedback.Answer(key, question, text, code);
    }

    private static CancellationFeedback sent(CancellationFeedback.Answer... answers) {
        // Shaped like a real request: the client sends no version and no submittedAt.
        return new CancellationFeedback(null, "WEB", "CHEAPER_ELSEWHERE", null, Arrays.asList(answers));
    }

    private static String store(CancellationFeedback sent) {
        return CancellationFeedbackCodec.toStoredJson(sent, NOW);
    }

    private static CancellationFeedback roundTrip(CancellationFeedback sent) {
        return CancellationFeedbackCodec.fromStoredJson(store(sent));
    }

    private static String repeat(char c, int times) {
        return String.valueOf(c).repeat(times);
    }

    // ── The happy path, and the shape the dashboard depends on ────────────────

    @Test
    void aQuestionnaireIsStoredAsTheSelfDescribingDocumentTheDashboardReads() {
        // The dashboard renders this list knowing no codes at all — that is the entire reason the
        // question and answer text are stored alongside them. If the text stopped being stored, a
        // tenth cancellation reason added in the storefront would render as a blank row until
        // somebody shipped a label table to the dashboard.
        String json = store(sent(
                answer("reason", "Why are you cancelling this order?", "I found it cheaper elsewhere",
                        "CHEAPER_ELSEWHERE"),
                answer("price", "What price did you find elsewhere?", "AED 1100.00", null),
                answer("where", "Where did you find it?", "Noon", "NOON")));

        assertNotNull(json);
        assertTrue(json.contains("\"question\":\"Why are you cancelling this order?\""),
                "the question text must be stored, not just its key: " + json);
        assertTrue(json.contains("\"answer\":\"I found it cheaper elsewhere\""), json);
        assertTrue(json.contains("\"code\":\"CHEAPER_ELSEWHERE\""), json);

        CancellationFeedback read = CancellationFeedbackCodec.fromStoredJson(json);
        assertEquals(3, read.answers().size());
        assertEquals("WEB", read.source());
        assertEquals("CHEAPER_ELSEWHERE", read.reasonCode());
        assertNull(read.answers().get(1).code(), "a free-text answer carries no code, and that is not an error");
    }

    // ── The server owns the version and the clock ─────────────────────────────

    @Test
    void theServerStampsTheVersionAndTheTimeOverWhateverTheClientClaimed() {
        // A client-supplied submittedAt is a client-supplied fact about our own database. Believing
        // it means a cancellation can be backdated out of this month's numbers, or dated into the
        // future, by editing a request body. And a client-supplied version means a document can
        // claim a shape this backend never wrote, which every future reader would then branch on.
        CancellationFeedback lying = new CancellationFeedback(
                99, "WEB", "CHANGED_MY_MIND", Instant.parse("1999-01-01T00:00:00Z"),
                List.of(answer("reason", "Why?", "Changed my mind", "CHANGED_MY_MIND")));

        CancellationFeedback stored = roundTrip(lying);

        assertEquals(CancellationFeedbackCodec.VERSION, stored.version());
        assertEquals(NOW, stored.submittedAt());
    }

    // ── Caps: truncate, never refuse ──────────────────────────────────────────

    @Test
    void anOverlongValueIsTruncatedRatherThanRefused() {
        CancellationFeedback stored = roundTrip(sent(answer(
                repeat('k', 500), repeat('q', 900), repeat('a', 5000), repeat('c', 500))));

        assertNotNull(stored, "a long answer must still cancel the order and still be recorded");
        CancellationFeedback.Answer a = stored.answers().get(0);
        assertEquals(CancellationFeedbackCodec.KEY_MAX, a.key().length());
        assertEquals(CancellationFeedbackCodec.QUESTION_MAX, a.question().length());
        assertEquals(CancellationFeedbackCodec.ANSWER_MAX, a.answer().length());
        assertEquals(CancellationFeedbackCodec.CODE_MAX, a.code().length());
    }

    @Test
    void anOverlongReasonCodeIsTruncatedToo() {
        CancellationFeedback stored = CancellationFeedbackCodec.fromStoredJson(
                CancellationFeedbackCodec.toStoredJson(new CancellationFeedback(
                        null, "WEB", repeat('R', 400), null,
                        List.of(answer("reason", "Why?", "Because", null))), NOW));

        assertEquals(CancellationFeedbackCodec.CODE_MAX, stored.reasonCode().length());
    }

    @Test
    void onlyTheFirstTwelveAnswersAreKept() {
        // A flow with three questions cannot produce twenty. Twenty means a loop bug or somebody
        // probing the endpoint, and either way the row must stay a row.
        List<CancellationFeedback.Answer> twenty = new ArrayList<>();
        for (int i = 0; i < 20; i++) twenty.add(answer("k" + i, "Q" + i, "A" + i, null));

        CancellationFeedback kept = CancellationFeedbackCodec.fromStoredJson(
                CancellationFeedbackCodec.toStoredJson(
                        new CancellationFeedback(null, "WEB", "OTHER", null, twenty), NOW));

        assertEquals(CancellationFeedbackCodec.MAX_ANSWERS, kept.answers().size());
        assertEquals("A0", kept.answers().get(0).answer(), "the first answers are the ones kept");
    }

    // ── Cleaning ──────────────────────────────────────────────────────────────

    @Test
    void controlCharactersAndNewlinesNeverReachStorage() {
        // These strings are rendered as list rows in the dashboard and appear in log lines and CSV
        // exports. An embedded newline turns one customer's answer into what looks like several
        // records; a control character corrupts the export around it.
        CancellationFeedback stored = roundTrip(sent(answer(
                "reason",
                "Why are you\tcancelling?",
                "Too\nexpensive\r\nfor  me\u0007 now",
                "TOO\u0000EXPENSIVE")));

        CancellationFeedback.Answer a = stored.answers().get(0);
        assertEquals("Why are you cancelling?", a.question());
        assertEquals("Too expensive for me now", a.answer(),
                "newlines collapse to single spaces and control characters disappear");
        assertEquals("TOOEXPENSIVE", a.code());
        assertFalse(store(sent(answer("reason", "Q", "a\nb", null))).contains("\\n"),
                "no escaped newline should survive into the stored document");
    }

    @Test
    void aBlankAnswerIsDroppedAndAnAllBlankQuestionnaireStoresNothing() {
        // An unanswered question is not data. Kept, it shows as an empty row in the dashboard and
        // counts as a response in the numbers this column exists to produce.
        CancellationFeedback partial = roundTrip(sent(
                answer("reason", "Why?", "Changed my mind", "CHANGED_MY_MIND"),
                answer("detail", "Anything else?", "   ", null),
                answer("other", "And?", "\n\t", null)));

        assertEquals(1, partial.answers().size());
        assertEquals("reason", partial.answers().get(0).key());

        assertNull(store(sent(
                answer("reason", "Why?", "", null),
                answer("detail", "Anything else?", "  \n ", null))),
                "a questionnaire with nothing answered stores NULL — never '{}', which would claim "
                        + "it was answered with nothing in it");
    }

    @Test
    void anUnknownSourceBecomesNullRatherThanBeingRecorded() {
        // WEB and MOBILE are the two clients. Anything else is a caller we cannot attribute, and
        // writing its own word for itself into an analytics field proves nothing.
        assertEquals("MOBILE", roundTrip(new CancellationFeedback(
                null, "mobile", null, null, List.of(answer("reason", "Why?", "No longer needed", null))))
                .source(), "case is not what makes a source untrustworthy");

        assertNull(roundTrip(new CancellationFeedback(
                null, "curl/8.4", null, null, List.of(answer("reason", "Why?", "No longer needed", null))))
                .source());
    }

    // ── The hard ceiling ──────────────────────────────────────────────────────

    @Test
    void theDocumentCeilingDropsTrailingAnswersRatherThanTruncatingTheJson() {
        // Truncating the serialized string would not produce a smaller document, it would produce an
        // unreadable one — and Postgres rejects invalid JSON for a jsonb column, which would roll
        // back the cancellation itself. Dropping whole trailing answers keeps it valid.
        List<CancellationFeedback.Answer> fat = new ArrayList<>();
        for (int i = 0; i < CancellationFeedbackCodec.MAX_ANSWERS; i++) {
            fat.add(answer("key" + i, repeat('q', 300), repeat('a', 600), "CODE" + i));
        }

        String json = CancellationFeedbackCodec.toStoredJson(
                new CancellationFeedback(null, "WEB", "OTHER", null, fat), NOW);

        assertNotNull(json);
        assertTrue(json.length() <= CancellationFeedbackCodec.MAX_JSON_CHARS,
                "stored " + json.length() + " chars, ceiling is " + CancellationFeedbackCodec.MAX_JSON_CHARS);
        CancellationFeedback read = CancellationFeedbackCodec.fromStoredJson(json);
        assertNotNull(read, "what is stored must still parse — that is the point of dropping answers");
        assertTrue(read.answers().size() < CancellationFeedbackCodec.MAX_ANSWERS,
                "trailing answers must actually have been dropped");
        assertEquals("key0", read.answers().get(0).key(), "the earliest answers are the ones kept");
    }

    // ── Absent feedback, and unreadable stored feedback ───────────────────────

    @Test
    void absentFeedbackStoresNothingAtAll() {
        // The path every admin cancellation and every not-yet-updated client takes. It must behave
        // exactly as it did before this column existed.
        assertNull(CancellationFeedbackCodec.toStoredJson(null, NOW));
        assertNull(CancellationFeedbackCodec.toStoredJson(
                new CancellationFeedback(null, null, null, null, null), NOW));
        assertNull(CancellationFeedbackCodec.toStoredJson(
                new CancellationFeedback(null, null, null, null, List.of()), NOW));
    }

    @Test
    void unreadableStoredJsonReadsBackAsNullInsteadOfThrowing() {
        // Whatever ends up in this column — hand-edited during an incident, written by a later
        // version, corrupted — the order has to stay viewable. A throw here takes out the customer's
        // order detail page and the admin's, for a field that is a note on the record.
        assertNull(CancellationFeedbackCodec.fromStoredJson(null));
        assertNull(CancellationFeedbackCodec.fromStoredJson(""));
        assertNull(CancellationFeedbackCodec.fromStoredJson("   "));
        assertNull(CancellationFeedbackCodec.fromStoredJson("{\"version\":1,\"answers\":["));
        assertNull(CancellationFeedbackCodec.fromStoredJson("not json at all"));
        assertNull(CancellationFeedbackCodec.fromStoredJson("[]"));
        assertNull(CancellationFeedbackCodec.fromStoredJson("{}"),
                "a document with no answers is indistinguishable from none");
        assertNull(CancellationFeedbackCodec.fromStoredJson("{\"submittedAt\":\"not-a-date\"}"));
    }

    // ── Binding the request: an extra key must not cost the cancellation ──────

    /**
     * Deliberately strict — {@code FAIL_ON_UNKNOWN_PROPERTIES} left at Jackson's default, which is
     * exactly how the application's {@code ObjectMapper} bean is configured. A test that turned the
     * feature off would pass whether or not the records tolerate an extra key, which is the only
     * thing being asserted here.
     */
    private static final ObjectMapper STRICT_LIKE_THE_APP =
            new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void anExtraKeyAnywhereInTheCancelBodyStillCancelsTheOrder() throws Exception {
        // This is the failure the caps in this class exist to prevent, arriving one layer earlier.
        // A rejected body never reaches the codec: Spring answers HTTP 400 and the shopper cannot
        // cancel their order at all — because a client added a field. Adding a field is the ordinary
        // way a questionnaire in two client repos grows, so this has to be survivable rather than
        // merely documented. The unknown keys are at all three levels: the body, the feedback
        // document, and one answer.
        String body = "{\"reason\":\"I found it cheaper elsewhere\","
                + "\"clientVersion\":\"4.2.0\","
                + "\"feedback\":{\"source\":\"WEB\",\"reasonCode\":\"CHEAPER_ELSEWHERE\",\"locale\":\"ar\","
                + "\"answers\":[{\"key\":\"reason\",\"question\":\"Why are you cancelling this order?\","
                + "\"answer\":\"I found it cheaper elsewhere\",\"code\":\"CHEAPER_ELSEWHERE\","
                + "\"answeredAt\":\"2026-09-29T10:15:00Z\"}]}}";

        OrderController.CancelOrderRequest parsed =
                STRICT_LIKE_THE_APP.readValue(body, OrderController.CancelOrderRequest.class);

        assertEquals("I found it cheaper elsewhere", parsed.reason(),
                "the reason still binds past the unknown keys");
        assertNotNull(parsed.feedback(), "the questionnaire must survive an unknown sibling key");

        // And what the client did send still reaches storage intact, through the node path the
        // controller actually hands the codec.
        CancellationFeedback stored =
                CancellationFeedbackCodec.fromStoredJson(
                        CancellationFeedbackCodec.toStoredJson(parsed.feedback()));
        assertNotNull(stored, "an unknown key must not cost the whole document");
        assertEquals("CHEAPER_ELSEWHERE", stored.reasonCode());
        assertEquals(1, stored.answers().size());
        assertEquals("I found it cheaper elsewhere", stored.answers().get(0).answer(),
                "the answer the customer gave must survive an unknown sibling key");
        assertEquals("Why are you cancelling this order?", stored.answers().get(0).question());
    }

    @Test
    void aFeedbackBlockJacksonCannotBindStillCancelsTheOrder() throws Exception {
        // The same failure as the test above, reached through a KNOWN key instead of an unknown one.
        // While `feedback` was bound to a record, Jackson was the first gate and its answer to a shape
        // it disliked was to fail the whole request: HTTP 400, and the shopper could not cancel at
        // all. Unknown keys were tolerated and known-but-malformed ones were fatal, which is the wrong
        // way round — the values are all distrusted and rebuilt downstream anyway, so type-checking
        // them here could only ever add a way for the analytics to take the cancellation down.
        //
        // Each of these bound to an exception before `feedback` became a JsonNode.
        String[] hostileFeedback = {
                "{\"source\":\"WEB\",\"submittedAt\":\"29/09/2026\",\"answers\":[]}",
                "{\"source\":\"WEB\",\"version\":\"1.0\",\"answers\":[]}",
                "{\"answers\":\"none\"}",
                "{\"answers\":{\"reason\":\"I changed my mind\"}}",
                "{\"answers\":[42]}",
                "{\"answers\":[{\"answer\":{\"nested\":\"object\"}}]}",
                "\"just a string\"",
                "[]",
                "null",
        };

        for (String feedback : hostileFeedback) {
            String body = "{\"reason\":\"I changed my mind\",\"feedback\":" + feedback + "}";

            OrderController.CancelOrderRequest parsed = assertDoesNotThrow(
                    () -> STRICT_LIKE_THE_APP.readValue(body, OrderController.CancelOrderRequest.class),
                    "binding must not refuse the cancellation over the questionnaire: " + feedback);

            assertEquals("I changed my mind", parsed.reason(),
                    "the reason is the half that matters and must survive: " + feedback);
            // Whatever it was, the codec stores nothing rather than throwing. Storing nothing is the
            // right outcome: there is no answer here anyone could trust.
            assertDoesNotThrow(() -> CancellationFeedbackCodec.toStoredJson(parsed.feedback()),
                    "the codec must absorb it, not rethrow: " + feedback);
            assertNull(CancellationFeedbackCodec.toStoredJson(parsed.feedback()),
                    "an unusable questionnaire stores nothing: " + feedback);
        }
    }

    @Test
    void aWellFormedNodeStillStoresEverythingTheCustomerAnswered() throws Exception {
        // The counterweight to the test above: leniency must not have become indifference. The happy
        // path has to still carry every answer through the node conversion.
        String body = "{\"reason\":\"Found it cheaper elsewhere — AED 1100.00 at Noon\","
                + "\"feedback\":{\"source\":\"MOBILE\",\"reasonCode\":\"CHEAPER_ELSEWHERE\",\"answers\":["
                + "{\"key\":\"reason\",\"question\":\"Why are you cancelling this order?\","
                + "\"answer\":\"I found it cheaper elsewhere\",\"code\":\"CHEAPER_ELSEWHERE\"},"
                + "{\"key\":\"price\",\"question\":\"What price did you find elsewhere?\","
                + "\"answer\":\"AED 1100.00\",\"code\":null},"
                + "{\"key\":\"where\",\"question\":\"Where did you find it?\","
                + "\"answer\":\"Noon\",\"code\":\"NOON\"}]}}";

        OrderController.CancelOrderRequest parsed =
                STRICT_LIKE_THE_APP.readValue(body, OrderController.CancelOrderRequest.class);
        CancellationFeedback stored = CancellationFeedbackCodec.fromStoredJson(
                CancellationFeedbackCodec.toStoredJson(parsed.feedback()));

        assertNotNull(stored);
        assertEquals("MOBILE", stored.source());
        assertEquals(3, stored.answers().size(), "all three answers, in order");
        assertEquals("Where did you find it?", stored.answers().get(2).question());
        assertEquals("Noon", stored.answers().get(2).answer());
        assertEquals("NOON", stored.answers().get(2).code());
        assertNull(stored.answers().get(1).code(), "an explicit null code stays null, not \"null\"");
    }

    @Test
    void anOverLongReasonIsClippedRatherThanRefusedByPostgres() {
        // The reason column is varchar(1000) and nothing used to approach it, because the value was
        // whatever an admin typed. It is now prose ASSEMBLED by two clients from a questionnaire, so a
        // long one is reachable — and an over-long one does not fail politely. The flush raises
        // Postgres 22001 inside applyCustomerCancellation, which is caught by the handler that has
        // ALREADY cancelled the courier job: courier stopped, order still live, stock withheld, a
        // superadmin paged, a 500 for the customer. Losing the tail of a sentence is not comparable.
        Order order = new Order();
        order.setCancellationReason("x".repeat(1500));

        assertEquals(1000, order.getCancellationReason().length(),
                "a reason longer than the column must be clipped to it, not handed to Postgres");

        order.setCancellationReason("I changed my mind");
        assertEquals("I changed my mind", order.getCancellationReason(),
                "an ordinary reason is untouched");

        order.setCancellationReason(null);
        assertNull(order.getCancellationReason(), "null is still null, not an empty string");
    }

    @Test
    void clippingAReasonNeverLeavesHalfAnEmoji() {
        // A cut that lands between the two halves of a surrogate pair produces a lone surrogate, which
        // is not a character and cannot be encoded to UTF-8 — so a naive substring turns a merely long
        // reason into one the driver cannot send, reaching the exact failure the clip exists to avoid.
        // 999 plain chars then an emoji puts the boundary inside the pair.
        Order order = new Order();
        order.setCancellationReason("x".repeat(999) + "\uD83D\uDE00");

        String clipped = order.getCancellationReason();
        assertEquals(999, clipped.length(), "the emoji is dropped whole rather than halved");
        assertFalse(Character.isHighSurrogate(clipped.charAt(clipped.length() - 1)),
                "a trailing lone surrogate cannot be written to a UTF-8 column");
        assertEquals(clipped.length(), clipped.codePointCount(0, clipped.length()),
                "every char in the result is a whole character");
    }

    @Test
    void anAnswerTruncatedAtItsCapIsStillValidText() {
        // The same surrogate hazard inside the codec's own per-field caps. ANSWER_MAX is 600, so an
        // emoji straddling char 600 would be halved by a char-indexed cut — and an invalid string does
        // not merely look wrong, it cannot be serialized, which loses the whole document.
        String padded = "y".repeat(599) + "\uD83D\uDE00" + "tail";
        CancellationFeedback stored = roundTrip(sent(answer("note", "What changed?", padded, null)));

        String text = stored.answers().get(0).answer();
        assertTrue(text.length() <= 600, "still capped");
        assertEquals(text.length(), text.codePointCount(0, text.length()),
                "no half-characters survive the cap");
        assertEquals("y".repeat(599), text,
                "the emoji that would have straddled the boundary is dropped whole");
    }

    @Test
    void invisibleDirectionCharactersDoNotReachTheDashboard() {
        // Bidi overrides and zero-width characters are invisible but are not control characters, so
        // isISOControl let them through. They matter because this text is rendered into an admin's
        // browser and into CSV exports: a right-to-left override reverses the rest of the line on
        // screen, so what the admin reads is not what the customer submitted.
        String hostile = "Bought\u202Eelsewhere\u200B today\uFEFF";
        CancellationFeedback stored = roundTrip(sent(answer("note", "What changed?", hostile, null)));

        String text = stored.answers().get(0).answer();
        assertEquals("Boughtelsewhere today", text,
                "the override, the zero-width space and the BOM are all gone");
        for (char c : text.toCharArray()) {
            assertFalse((c >= 0x200B && c <= 0x200F) || (c >= 0x202A && c <= 0x202E)
                            || (c >= 0x2066 && c <= 0x2069) || c == 0xFEFF,
                    "no invisible formatting character may survive cleaning");
        }
    }

    @Test
    void anEmptyCancelBodyStillBindsAsItAlwaysDid() throws Exception {
        OrderController.CancelOrderRequest bare =
                STRICT_LIKE_THE_APP.readValue("{}", OrderController.CancelOrderRequest.class);

        assertNull(bare.reason());
        assertNull(bare.feedback(), "no questionnaire is the admin path and every un-updated client");
    }

    @Test
    void anUnknownFieldInStoredJsonStillReads() {
        // A rolling deploy runs two versions at once. An order cancelled by the newer one must not
        // become unviewable on the older one because its document grew a field.
        CancellationFeedback read = CancellationFeedbackCodec.fromStoredJson(
                "{\"version\":2,\"source\":\"WEB\",\"somethingNew\":{\"a\":1},"
                        + "\"answers\":[{\"key\":\"reason\",\"question\":\"Why?\",\"answer\":\"No\",\"code\":null}]}");

        assertNotNull(read);
        assertEquals("No", read.answers().get(0).answer());
    }
}
