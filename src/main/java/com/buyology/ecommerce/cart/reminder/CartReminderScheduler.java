package com.buyology.ecommerce.cart.reminder;

import com.buyology.ecommerce.common.scheduling.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * Runs the cart sweep once an hour, once across the fleet.
 *
 * <p>Both application hosts run every {@code @Scheduled} method, and this has already cost
 * customers once: the streak reminder shipped without a claim and every customer received it
 * twice. So the hour is claimed before a single message is sent.
 *
 * <p>{@link SchedulerLock} can only express "once per task per day" — its unique key is
 * (task name, date). The hour therefore goes into the task NAME, which turns the day-granular lock
 * into an hourly one without touching the shared table or the other twenty jobs that depend on it.
 * The name stays well inside the column's 80 characters.
 *
 * <p>The claim is taken before the work and never released, so a host that dies mid-sweep skips
 * that hour rather than letting the other host repeat it. For a reminder that is the right trade:
 * a cart missed at 10:00 is found at 11:00, while a duplicate is in someone's inbox forever.
 */
@Component
public class CartReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(CartReminderScheduler.class);

    private final CartReminderService service;
    private final SchedulerLock lock;

    public CartReminderScheduler(CartReminderService service, SchedulerLock lock) {
        this.service = service;
        this.lock = lock;
    }

    @Scheduled(cron = "0 5 * * * *", zone = "UTC")
    public void sweep() {
        Instant hour = Instant.now().truncatedTo(ChronoUnit.HOURS);
        String task = "cart-reminder-" + hour.atZone(ZoneOffset.UTC).getHour();
        boolean claimed;
        try {
            claimed = lock.claim(task, hour.atZone(ZoneOffset.UTC).toLocalDate());
        } catch (Exception losingTheRace) {
            // The claim's own catch returns false, but a rolled-back REQUIRES_NEW transaction can
            // still throw at its commit boundary. That is the lock working, not failing, so it is
            // noted as a fact rather than as an hourly stack trace on whichever host loses.
            log.debug("[CART-REMINDER] hour {} is claimed by the other host", task);
            return;
        }
        if (!claimed) return; // the other host has this hour
        try {
            service.sendDueReminders();
        } catch (Exception e) {
            log.error("[CART-REMINDER] sweep failed: {}", e.toString(), e);
        }
    }
}
