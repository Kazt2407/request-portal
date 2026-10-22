package dev.requestportal.notification.event;

import dev.requestportal.notification.notification.NotificationKind;
import dev.requestportal.notification.notification.NotificationQueue;
import dev.requestportal.notification.reminder.TrackedSteps;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decides what each event means for notifications. Runs in one transaction with the processed-event marker,
 * so an event is acted on exactly once even though Kafka may deliver it more than once.
 */
@Component
class RequestEventHandler {

    private static final Logger log = LoggerFactory.getLogger(RequestEventHandler.class);

    private final ProcessedEvents processedEvents;
    private final NotificationQueue notifications;
    private final TrackedSteps trackedSteps;

    RequestEventHandler(ProcessedEvents processedEvents, NotificationQueue notifications, TrackedSteps trackedSteps) {
        this.processedEvents = processedEvents;
        this.notifications = notifications;
        this.trackedSteps = trackedSteps;
    }

    @Transactional
    void handle(RequestEvent event) {
        if (!processedEvents.markProcessed(event.eventId(), event.type())) {
            log.debug("Skipping event {} ({}): already handled", event.eventId(), event.type());
            return;
        }
        switch (event.type()) {
            case "REQUEST_SUBMITTED" -> notifications.toRequester(event, NotificationKind.REQUEST_SUBMITTED,
                    Map.of("expectedDays", event.number("expectedDays") == null ? "" : event.number("expectedDays")));
            case "STEP_STARTED" -> {
                trackedSteps.started(event);
                notifications.toDepartment(event, NotificationKind.STEP_WAITING, event.text("departmentCode"),
                        Map.of("stepDescription", event.text("description"), "dueAt", event.instant("dueAt")));
            }
            case "STEP_FINISHED" -> trackedSteps.finished(event);
            case "REQUEST_COMPLETED" -> notifications.toRequester(event, NotificationKind.REQUEST_COMPLETED,
                    Map.of("resultDepartmentCode", event.text("resultDepartmentCode")));
            case "REQUEST_REJECTED" -> notifications.toRequester(event, NotificationKind.REQUEST_REJECTED,
                    Map.of("reason", event.text("reason"), "rejectingDepartmentCode", event.text("departmentCode")));
            case "REQUEST_CANCELLED" -> notifications.toRequester(event, NotificationKind.REQUEST_CANCELLED, Map.of());
            // Newer request-service versions may add types; ignoring them keeps this consumer working.
            default -> log.info("Ignoring event {} of unknown type {}", event.eventId(), event.type());
        }
    }
}
