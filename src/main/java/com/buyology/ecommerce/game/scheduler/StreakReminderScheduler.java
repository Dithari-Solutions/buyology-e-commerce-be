package com.buyology.ecommerce.game.scheduler;

import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import com.buyology.ecommerce.common.service.EmailService;
import com.buyology.ecommerce.common.scheduling.SchedulerLock;
import com.buyology.ecommerce.game.domain.UserStreak;
import com.buyology.ecommerce.game.repository.UserStreakRepository;
import com.buyology.ecommerce.notification.service.PushNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Component
public class StreakReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(StreakReminderScheduler.class);

    private final UserStreakRepository userStreakRepo;
    private final SchedulerLock schedulerLock;
    private final PushNotificationService pushService;
    private final EmailService emailService;
    private final AuthCredentialRepository authCredentialRepo;

    public StreakReminderScheduler(UserStreakRepository userStreakRepo,
                                   SchedulerLock schedulerLock,
                                   PushNotificationService pushService,
                                   EmailService emailService,
                                   AuthCredentialRepository authCredentialRepo) {
        this.userStreakRepo = userStreakRepo;
        this.schedulerLock = schedulerLock;
        this.pushService = pushService;
        this.emailService = emailService;
        this.authCredentialRepo = authCredentialRepo;
    }

    /**
     * Closes streaks that have already lapsed, before anything reads them.
     *
     * <p>Runs first thing so the rest of the day tells the truth. Without it the number survives
     * until the customer next plays: the reminder promises to save a streak that ended a week ago,
     * the account screen shows it, and then playing silently drops it to 1.
     */
    @Scheduled(cron = "0 5 0 * * ?")
    public void closeBrokenStreaks() {
        LocalDate today = LocalDate.now();
        if (!schedulerLock.claim("streak-close-broken", today)) return;

        List<UserStreak> broken = userStreakRepo.findBrokenStreaks(today.minusDays(1));
        if (broken.isEmpty()) return;

        for (UserStreak streak : broken) {
            int lost = streak.getCurrentStreak();
            streak.setCurrentStreak(0);
            userStreakRepo.save(streak);
            try {
                // Told once, when it actually happens — not disguised as a reminder to keep
                // something that is already gone.
                pushService.sendToUser(streak.getUser().getId(),
                        "Your streak ended",
                        "Your " + lost + "-day streak has ended. Play today to start a new one.",
                        Map.of("type", "STREAK_LOST", "streak", String.valueOf(lost)));
            } catch (Exception e) {
                log.warn("[STREAK] Could not tell user {} their streak ended: {}",
                        streak.getUser().getId(), e.getMessage());
            }
        }
        log.info("[STREAK] Closed {} broken streak(s)", broken.size());
        schedulerLock.purgeBefore(today.minusDays(7));
    }

    /**
     * Runs every day at 6 PM server time.
     *
     * <p>Claimed first: the backend runs on two servers, so without this every customer received
     * the reminder twice — the same notification, seconds apart, from two instances that had no
     * idea about each other.
     */
    @Scheduled(cron = "0 0 18 * * ?")
    public void sendStreakReminders() {
        LocalDate today = LocalDate.now();
        if (!schedulerLock.claim("streak-reminder", today)) return;

        // Only streaks that are genuinely alive and at risk TODAY.
        List<UserStreak> activeStreaks = userStreakRepo.findStreaksAtRiskToday(today.minusDays(1));

        log.info("[STREAK] Sending reminders to {} users", activeStreaks.size());

        for (UserStreak streak : activeStreaks) {
            try {
                int count = streak.getCurrentStreak();
                String title = "Don't break your streak! \uD83D\uDD25";
                String body = "Play today's game to keep your " + count + "-day streak alive!";

                pushService.sendToUser(streak.getUser().getId(), title, body,
                        Map.of("type", "STREAK_REMINDER", "streak", String.valueOf(count)));

                authCredentialRepo.findByUserId(streak.getUser().getId()).stream()
                        .map(c -> c.getEmail())
                        .filter(e -> e != null && !e.isBlank())
                        .findFirst()
                        .ifPresent(email -> emailService.sendStreakReminderEmail(email, count));

            } catch (Exception e) {
                log.warn("[STREAK] Failed to notify user {}: {}", streak.getUser().getId(), e.getMessage());
            }
        }
    }
}
