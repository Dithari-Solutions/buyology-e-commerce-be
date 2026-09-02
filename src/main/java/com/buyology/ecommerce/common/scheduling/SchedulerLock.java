package com.buyology.ecommerce.common.scheduling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Runs a scheduled task once per day across every instance.
 *
 * <p>The backend is deployed to two servers, so every {@code @Scheduled} method fires on both.
 * For a cache sweep that is merely wasteful; for anything that emails or notifies a customer it
 * means they get it twice, which is what was happening with the streak reminder.
 *
 * <p>The claim is a single INSERT against a unique (task, date) index. Whoever inserts first has
 * the day; the loser catches the constraint violation and does nothing. No lease to expire, no
 * clock skew to reason about, and an instance that dies mid-task simply means the task does not
 * run again that day — which is the right failure for a notification, where a miss is far cheaper
 * than a duplicate.
 */
@Service
public class SchedulerLock {

    private static final Logger log = LoggerFactory.getLogger(SchedulerLock.class);

    private final ScheduledTaskRunRepository repository;

    public SchedulerLock(ScheduledTaskRunRepository repository) {
        this.repository = repository;
    }

    /**
     * @return true if THIS instance won the day for {@code taskName} and should proceed
     */
    // REQUIRES_NEW so the claim commits on its own. Sharing the caller's transaction would roll
    // the claim back with any later failure, letting the other instance pick the task up and send
    // a second copy of whatever had already gone out.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(String taskName, LocalDate day) {
        try {
            repository.saveAndFlush(new ScheduledTaskRun(taskName, day));
            return true;
        } catch (DataIntegrityViolationException alreadyClaimed) {
            log.debug("[SCHEDULER] '{}' for {} is already claimed by another instance", taskName, day);
            return false;
        }
    }

    /** Yesterday's claims are of no further use. */
    @Transactional
    public void purgeBefore(LocalDate day) {
        int removed = repository.deleteOlderThan(day);
        if (removed > 0) log.debug("[SCHEDULER] purged {} old task claims", removed);
    }
}
