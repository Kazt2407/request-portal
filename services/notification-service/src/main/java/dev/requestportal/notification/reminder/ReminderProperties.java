package dev.requestportal.notification.reminder;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param interval      how often to look for due steps
 * @param dueSoonWindow how long before the deadline to send the reminder
 */
@ConfigurationProperties("portal.reminders")
public record ReminderProperties(Duration interval, Duration dueSoonWindow) {
}
