package com.buyology.ecommerce.game.scheduler;

import com.buyology.ecommerce.auth.repository.AuthCredentialRepository;
import com.buyology.ecommerce.common.service.EmailService;
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
    private final PushNotificationService pushService;
    private final EmailService emailService;
    private final AuthCredentialRepository authCredentialRepo;

    public StreakReminderScheduler(UserStreakRepository userStreakRepo,
                                   PushNotificationService pushService,
                                   EmailService emailService,
                                   AuthCredentialRepository authCredentialRepo) {
        this.userStreakRepo = userStreakRepo;
        this.pushService = pushService;
        this.emailService = emailService;
        this.authCredentialRepo = authCredentialRepo;
    }

    /** Runs every day at 6 PM server time. */
    @Scheduled(cron = "0 0 18 * * ?")
    public void sendStreakReminders() {
        LocalDate today = LocalDate.now();
        List<UserStreak> activeStreaks = userStreakRepo.findActiveStreaksNotPlayedToday(today);

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
