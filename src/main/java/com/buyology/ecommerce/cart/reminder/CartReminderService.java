package com.buyology.ecommerce.cart.reminder;

import com.buyology.ecommerce.common.service.EmailService;
import com.buyology.ecommerce.notification.service.PushNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Writes to customers who filled a cart and did not order.
 *
 * <p>One email per abandonment, not a sequence: a second and third reminder to a customer base
 * this size wears out the welcome faster than it earns orders, and the re-arm rule in
 * {@link CartReminderRepository} already gives a shopper who comes back and adds something new a
 * fresh reminder. No discount either — once shoppers learn that leaving a cart produces a promo
 * code, they leave carts on purpose.
 *
 * <p>Reaches signed-in customers only, and that is a property of the data rather than a choice: a
 * guest's cart lives in their browser and never reaches this database, so there is no address to
 * write to.
 */
@Service
public class CartReminderService {

    private static final Logger log = LoggerFactory.getLogger(CartReminderService.class);

    private final CartReminderRepository repository;
    private final EmailService emailService;
    private final PushNotificationService notifications;
    private final boolean enabled;
    private final Duration quietPeriod;
    private final Duration maxAge;
    private final int batchSize;
    private final String subject;
    private final String baseUrl;

    public CartReminderService(CartReminderRepository repository,
                               EmailService emailService,
                               PushNotificationService notifications,
                               @Value("${app.cart-reminder.enabled:true}") boolean enabled,
                               @Value("${app.cart-reminder.quiet-hours:4}") long quietHours,
                               @Value("${app.cart-reminder.max-age-days:7}") long maxAgeDays,
                               @Value("${app.cart-reminder.batch-size:100}") int batchSize,
                               @Value("${app.cart-reminder.subject:You left something in your cart}") String subject,
                               @Value("${app.base-url:https://buyology.online}") String baseUrl) {
        this.repository = repository;
        this.emailService = emailService;
        this.notifications = notifications;
        this.enabled = enabled;
        this.quietPeriod = Duration.ofHours(quietHours);
        this.maxAge = Duration.ofDays(maxAgeDays);
        this.batchSize = batchSize;
        this.subject = subject;
        this.baseUrl = baseUrl;
    }

    /**
     * Sends one round of reminders and returns how many went out.
     *
     * <p>Bounded at both ends of time as well as in size. The lower bound is what keeps the first
     * run in production from being a broadcast: nothing here has ever run, so every cart ever
     * abandoned carries a null reminder stamp, and without a floor the first sweep would work its
     * way through years of them.
     *
     * <p>Sends are sequential and the batch is capped. The shared {@code @Async} pool runs on
     * CallerRunsPolicy with a 500-deep queue, and it is the same pool that settles payments — a
     * sweep that fired every send at it would, on a busy morning, start executing them inline on
     * the scheduler thread and stall the jobs beside it. A hundred sequential sends per hour is
     * also simply a kinder shape for a sending domain with no bounce handling yet.
     */
    public int sendDueReminders() {
        if (!enabled) {
            log.debug("[CART-REMINDER] disabled by configuration");
            return 0;
        }
        Instant now = Instant.now();
        List<CartReminderRepository.Candidate> due =
                repository.findCandidates(now.minus(quietPeriod), now.minus(maxAge), batchSize);
        if (due.isEmpty()) return 0;

        int sent = 0;
        for (CartReminderRepository.Candidate candidate : due) {
            if (remind(candidate)) sent++;
        }
        log.info("[CART-REMINDER] {} of {} due carts reminded", sent, due.size());
        return sent;
    }

    private boolean remind(CartReminderRepository.Candidate candidate) {
        try {
            List<CartReminderRepository.Line> lines = repository.findLines(candidate.cartId());
            // The cart emptied between the sweep and now. Nothing to write about, and stamping it
            // would be a lie, so leave it for a future sweep to find if it fills up again.
            if (lines.isEmpty()) return false;

            String currency = candidate.currency() == null || candidate.currency().isBlank()
                    ? "AED" : candidate.currency();
            BigDecimal total = lines.stream()
                    .map(CartReminderRepository.Line::totalPrice)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            List<EmailService.CartLine> emailLines = lines.stream()
                    .map(l -> new EmailService.CartLine(l.title(), l.quantity(), money(currency, l.totalPrice())))
                    .toList();

            // Through sign-in, because the cart lives on the server and the browser that opens the
            // email may not know who the reader is. A signed-in reader passes straight through;
            // a signed-out one signs in and lands on the same page, instead of on an empty cart.
            String cartUrl = baseUrl + "/login?next=%2Fcart";
            String unsubscribe = baseUrl + "/api/email/opt-out?token=" + candidate.optOutToken();

            boolean emailed = emailService.sendAbandonedCartEmail(
                    candidate.email(), subject, candidate.firstName(),
                    emailLines, money(currency, total), cartUrl, unsubscribe);

            // The stamp records the attempt's outcome. A failed send leaves the cart armed, so the
            // next sweep tries again rather than silently dropping the customer.
            if (!emailed) return false;
            repository.markReminded(candidate.cartId(), Instant.now());

            // The bell, in the site the customer already uses. Best-effort and deliberately after
            // the stamp: a missing bell row is not worth re-sending an email over.
            try {
                notifications.sendToUser(
                        candidate.userId(),
                        "Your cart is waiting",
                        lines.size() == 1
                                ? "1 item is still in your cart."
                                : lines.size() + " items are still in your cart.",
                        "CART_REMINDER",
                        Map.of("cartId", candidate.cartId().toString()));
            } catch (Exception e) {
                log.warn("[CART-REMINDER] cart {} emailed but the bell failed: {}",
                        candidate.cartId(), e.getMessage());
            }
            return true;
        } catch (Exception e) {
            // One broken cart must not end the round for everyone behind it.
            log.warn("[CART-REMINDER] cart {} skipped: {}", candidate.cartId(), e.toString());
            return false;
        }
    }

    /** Matches the money format the other emails use: "AED 899.00". */
    private static String money(String currency, BigDecimal amount) {
        BigDecimal a = (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
        return currency + " " + a.toPlainString();
    }
}
