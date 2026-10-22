package dev.requestportal.notification.notification;

import dev.requestportal.notification.org.DepartmentDirectory;
import dev.requestportal.web.UpstreamServiceException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Delivers queued notifications by email, retrying failures with backoff. */
@Component
public class NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(NotificationSender.class);

    private final NotificationRepository notifications;
    private final DepartmentDirectory departments;
    private final JavaMailSender mail;
    private final MailProperties mailProperties;
    private final SenderProperties properties;
    private final TransactionTemplate transaction;
    private final Clock clock;

    NotificationSender(NotificationRepository notifications, DepartmentDirectory departments, JavaMailSender mail,
            MailProperties mailProperties, SenderProperties properties,
            PlatformTransactionManager transactionManager, Clock clock) {
        this.notifications = notifications;
        this.departments = departments;
        this.mail = mail;
        this.mailProperties = mailProperties;
        this.properties = properties;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${portal.sender.poll-interval}")
    void sendDue() {
        int handled;
        do {
            handled = sendBatch();
        } while (handled == properties.batchSize());
    }

    /** Sends one batch of due notifications; returns how many were handled. */
    public int sendBatch() {
        Integer handled = transaction.execute(status -> {
            Instant now = clock.instant();
            List<Notification> due = notifications.lockDue(now, properties.batchSize());
            due.forEach(notification -> deliver(notification, now));
            return due.size();
        });
        return handled == null ? 0 : handled;
    }

    private void deliver(Notification notification, Instant now) {
        Optional<String> recipient;
        try {
            recipient = recipientOf(notification);
        } catch (UpstreamServiceException exception) {
            notification.deferred(exception.getDetail(), now);
            return;
        }
        if (recipient.isEmpty()) {
            notification.skipped("Department " + notification.getRecipientDepartment() + " has no mailbox");
            return;
        }
        try {
            mail.send(message(notification, recipient.get()));
            notification.sent(now);
        } catch (MailException | MessagingException exception) {
            log.warn("Sending notification {} to {} failed", notification.getId(), recipient.get(), exception);
            notification.attemptFailed(exception.getMessage(), now, properties.maxAttempts());
        }
    }

    private Optional<String> recipientOf(Notification notification) {
        if (notification.getRecipientEmail() != null) {
            return Optional.of(notification.getRecipientEmail());
        }
        return departments.find(notification.getRecipientDepartment())
                .map(DepartmentDirectory.Department::email)
                .filter(email -> !email.isBlank());
    }

    private MimeMessage message(Notification notification, String recipient) throws MessagingException {
        MimeMessage message = mail.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
        helper.setFrom(mailProperties.from());
        helper.setTo(recipient);
        helper.setSubject(notification.getSubject());
        helper.setText(notification.getBody(), true);
        return message;
    }
}
