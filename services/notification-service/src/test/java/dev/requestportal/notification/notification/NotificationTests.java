package dev.requestportal.notification.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationTests {

    private static final Instant NOW = Instant.parse("2026-10-22T08:00:00Z");

    @Test
    void failedAttemptsBackOffThenGiveUp() {
        Notification notification = Notification.toEmail(UUID.randomUUID(), 1, "ACAD-0001",
                NotificationKind.REQUEST_SUBMITTED, "an@example.edu", "Subject", "<p>Body</p>", NOW);

        notification.attemptFailed("timeout", NOW, 3);
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getNextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(1)));

        notification.attemptFailed("timeout", NOW, 3);
        assertThat(notification.getNextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));

        notification.attemptFailed("still down", NOW, 3);
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getAttempts()).isEqualTo(3);
        assertThat(notification.getLastError()).isEqualTo("still down");

        notification.retry(NOW);
        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(notification.getAttempts()).isZero();
    }

    @Test
    void subjectsStartWithTheRequestCode() {
        assertThat(NotificationQueue.subject(NotificationKind.STEP_OVERDUE, "FIN-0007", "Tuition fee receipt"))
                .isEqualTo("[FIN-0007] Overdue: Tuition fee receipt");
    }
}
