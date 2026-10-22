package dev.requestportal.notification.reminder;

import dev.requestportal.notification.notification.NotificationKind;
import dev.requestportal.notification.notification.NotificationQueue;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Queues "due soon" and "overdue" emails to departments. Safe to run on every instance at once: each step
 * is claimed by an atomic update, so no distributed lock is needed.
 */
@Component
public class ReminderJob {

    private static final Logger log = LoggerFactory.getLogger(ReminderJob.class);

    private final TrackedSteps steps;
    private final NotificationQueue notifications;
    private final ReminderProperties properties;
    private final Clock clock;

    ReminderJob(TrackedSteps steps, NotificationQueue notifications, ReminderProperties properties, Clock clock) {
        this.steps = steps;
        this.notifications = notifications;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${portal.reminders.interval}", initialDelayString = "${portal.reminders.interval}")
    @Transactional
    public void run() {
        Instant now = clock.instant();
        List<DueStep> dueSoon = steps.claimDueSoon(now, properties.dueSoonWindow());
        dueSoon.forEach(step -> notifications.reminder(step, NotificationKind.STEP_DUE_SOON));
        List<DueStep> overdue = steps.claimOverdue(now);
        overdue.forEach(step -> notifications.reminder(step, NotificationKind.STEP_OVERDUE));
        if (!dueSoon.isEmpty() || !overdue.isEmpty()) {
            log.info("Queued {} due-soon and {} overdue reminders", dueSoon.size(), overdue.size());
        }
    }
}
